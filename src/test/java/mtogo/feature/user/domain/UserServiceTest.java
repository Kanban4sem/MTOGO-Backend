package mtogo.feature.user.domain;

import static org.junit.jupiter.api.Assertions.*;

import mtogo.exceptions.ApiException;
import mtogo.exceptions.UnauthorizedException;
import mtogo.feature.user.presentation.dto.request.LoginUserDTO;
import mtogo.feature.user.presentation.dto.request.RegisterUserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"FEATURE_AUTH_ENABLED=true",
        "JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef"})
@Testcontainers
class UserServiceTest
{
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    UserService users;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwords;

    @BeforeEach
    void cleanAccounts()
    {
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM addresses");
        jdbc.update("DELETE FROM postal_codes");
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/user/user-service/register-profile.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Register - persists a CUSTOMER with the complete profile and optional address")
    void registerProfile(String language, boolean hasAddress, String expected, String reason)
    {
        var request = registration("CUSTOMER@EXAMPLE.TEST", language, hasAddress, "Customer123!");
        var result = users.register(request);
        assertEquals("customer@example.test", result.email());
        assertEquals("CUSTOMER", result.role());
        assertEquals("Jonas", result.profile().firstName());
        assertEquals("Tester", result.profile().lastName());
        assertEquals(hasAddress ? "12345678" : null, result.profile().phoneNumber());
        assertEquals(language == null ? "da" : language, result.profile().preferredLanguage());
        assertEquals(result, users.getCurrent(result.id()));
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, result.id());
        assertNotEquals(request.password(), hash);
        assertTrue(passwords.matches(request.password(), hash));
        assertEquals(Boolean.TRUE,
                jdbc.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, result.id()));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM user_profiles", Long.class));
        assertEquals(hasAddress ? 1L : 0L, jdbc.queryForObject("SELECT count(*) FROM addresses", Long.class));
        assertEquals(hasAddress ? 1L : 0L, jdbc.queryForObject("SELECT count(*) FROM postal_codes", Long.class));
        if (hasAddress)
        {
            assertEquals("1000", result.profile().address().postalCode());
            assertEquals("Copenhagen", result.profile().address().city());
            assertEquals("Denmark", result.profile().address().country());
            assertTrue(result.profile().address().isDefault());
        } else
        {
            assertNull(result.profile().address());
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/user/user-service/register-conflicts.csv", numLinesToSkip = 1)
    @DisplayName("Register - rejects mismatched passwords and normalized duplicate email without orphan data")
    void registerConflicts(boolean duplicate, boolean matching, String expected, String reason)
    {
        if (duplicate)
        {
            users.register(registration("customer@example.test", null, true, "Customer123!"));
        }
        var request = registration("CUSTOMER@EXAMPLE.TEST", null, true, matching ? "Customer123!" : "Different123!");
        ApiException exception = assertThrows(ApiException.class, () -> users.register(request));
        assertEquals(expected, exception.getClass().getSimpleName());
        long count = duplicate ? 1L : 0L;
        assertEquals(count, jdbc.queryForObject("SELECT count(*) FROM users", Long.class));
        assertEquals(count, jdbc.queryForObject("SELECT count(*) FROM user_profiles", Long.class));
        assertEquals(count, jdbc.queryForObject("SELECT count(*) FROM addresses", Long.class));
        assertEquals(count, jdbc.queryForObject("SELECT count(*) FROM postal_codes", Long.class));
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/user/user-service/login-credentials.csv", numLinesToSkip = 1)
    @DisplayName("Login - only correct credentials for an active known account issue a token")
    void loginCredentials(boolean exists, boolean active, boolean matching, String expected, String reason)
    {
        var account = users.register(registration("customer@example.test", null, false, "Customer123!"));
        jdbc.update("UPDATE users SET is_active = ? WHERE id = ?", active, account.id());
        var request = new LoginUserDTO(exists ? "CUSTOMER@EXAMPLE.TEST" : "unknown@example.test",
                matching ? "Customer123!" : "Different123!");
        if (expected.equals("ACCEPT"))
        {
            var authenticated = users.login(request);
            assertEquals("Bearer", authenticated.tokenType());
            assertEquals(account, authenticated.user());
            assertEquals(account, users.authenticate(authenticated.accessToken()));
        } else
        {
            var exception = assertThrows(UnauthorizedException.class, () -> users.login(request));
            assertEquals(expected, exception.getClass().getSimpleName());
            assertEquals("Invalid email or password", exception.getBody().getDetail());
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/user/user-service/authenticate-account.csv", numLinesToSkip = 1)
    @DisplayName("Authenticate - tokens require a currently active account with the current session version")
    void authenticateAccount(String state, String role, String expected, String reason)
    {
        var account = users.register(registration("customer@example.test", null, false, "Customer123!"));
        String token = users.login(new LoginUserDTO(account.email(), "Customer123!")).accessToken();
        jdbc.update("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = ?) WHERE id = ?", role,
                account.id());
        switch (state)
        {
            case "deleted" -> jdbc.update("DELETE FROM users WHERE id = ?", account.id());
            case "inactive" -> jdbc.update("UPDATE users SET is_active = false WHERE id = ?", account.id());
            default -> {
            }
        }
        if (expected.equals("ACCEPT"))
        {
            var result = users.authenticate(token);
            assertEquals(account.id(), result.id());
            assertEquals(role, result.role());
        } else
        {
            assertThrows(UnauthorizedException.class, () -> users.authenticate(token));
            assertThrows(UnauthorizedException.class, () -> users.getCurrent(account.id()));
        }
    }

    @Test
    @DisplayName("Logout - revokes only the selected account and preserves another user's token")
    void logoutIsolation()
    {
        var first = users.register(registration("first@example.test", null, false, "Customer123!"));
        var second = users.register(registration("second@example.test", null, false, "Customer123!"));
        String firstToken = users.login(new LoginUserDTO(first.email(), "Customer123!")).accessToken();
        String secondToken = users.login(new LoginUserDTO(second.email(), "Customer123!")).accessToken();
        users.logout(first.id());
        assertThrows(UnauthorizedException.class, () -> users.authenticate(firstToken));
        assertEquals(second, users.authenticate(secondToken));
        assertEquals(1L, jdbc.queryForObject("SELECT session_version FROM users WHERE id = ?", Long.class, first.id()));
        assertEquals(0L,
                jdbc.queryForObject("SELECT session_version FROM users WHERE id = ?", Long.class, second.id()));
        String freshToken = users.login(new LoginUserDTO(first.email(), "Customer123!")).accessToken();
        assertEquals(first, users.authenticate(freshToken));
    }

    private static RegisterUserDTO registration(String email, String language, boolean hasAddress, String confirmation)
    {
        return new RegisterUserDTO("Jonas", "Tester", email, "Customer123!", confirmation,
                hasAddress ? "12345678" : null, language, hasAddress ? "1000" : null, hasAddress ? "Copenhagen" : null,
                hasAddress ? "Denmark" : null);
    }
}
