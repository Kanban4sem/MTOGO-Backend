package mtogo.feature.user.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"FEATURE_AUTH_ENABLED=true",
        "JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef"})
@Testcontainers
class UserControllerTest
{
    @Container
    @ServiceConnection
    static org.testcontainers.postgresql.PostgreSQLContainer postgres = new org.testcontainers.postgresql.PostgreSQLContainer(
            "postgres:17-alpine");

    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwords;

    @BeforeEach
    void cleanAccounts()
    {
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM user_profiles WHERE id NOT IN (SELECT profile_id FROM users)");
        jdbc.update(
                "DELETE FROM addresses WHERE id NOT IN (SELECT address_id FROM user_profiles WHERE address_id IS NOT NULL)");
        jdbc.update("DELETE FROM postal_codes WHERE id NOT IN (SELECT postal_code_id FROM addresses)");
    }

    @Test
    @DisplayName("Register, login and logout - persists a customer and profile and revokes all prior tokens")
    void customerLifecycle()
    {
        Map<String, Object> request = registration();
        given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users").then().statusCode(201)
                .body("email", equalTo("customer@example.test")).body("role", equalTo("CUSTOMER"))
                .body("profile.firstName", equalTo("Jonas")).body("profile.preferredLanguage", equalTo("da"))
                .body("profile.address.city", equalTo("Copenhagen")).body("passwordHash", nullValue())
                .body("password", nullValue());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'customer@example.test'",
                String.class);
        assertNotEquals("Customer123!", hash);
        assertTrue(passwords.matches("Customer123!", hash));
        String first = login("CUSTOMER");
        String second = login("CUSTOMER");
        given().port(port).auth().oauth2(first).get("/v1/users/me").then().statusCode(200).body("email",
                equalTo("customer@example.test"));
        given().port(port).auth().oauth2(first).post("/v1/users/logout").then().statusCode(204);
        for (String token : new String[]{first, second})
        {
            given().port(port).auth().oauth2(token).get("/v1/users/me").then().statusCode(401);
        }
        given().port(port).auth().oauth2(login("CUSTOMER")).get("/v1/users/me").then().statusCode(200);
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/user/user-controller/register-fields.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Register - validates required fields, password boundaries and complete address groups")
    void registerFields(String field, String value, int expected, String reason)
    {
        Map<String, Object> request = registration();
        if (field.equals("withoutAddress"))
        {
            request.remove("postalCode");
            request.remove("city");
            request.remove("country");
        } else if (field.equals("passwordLength"))
        {
            request.put("password", "a".repeat(Integer.parseInt(value)));
            request.put("confirmPassword", request.get("password"));
        } else if (field.equals("unicodePassword"))
        {
            request.put("password", "\u00e9".repeat(Integer.parseInt(value)));
            request.put("confirmPassword", request.get("password"));
        } else
        {
            request.put(field, value);
        }
        given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users").then().statusCode(expected);
        assertEquals(expected == 201 ? 1L : 0L,
                jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'customer@example.test'", Long.class));
        if (expected != 201)
        {
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM addresses", Long.class));
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/user/user-controller/register-lengths.csv", numLinesToSkip = 1)
    @DisplayName("Register - enforces the documented maximum lengths of profile and address fields")
    void registerLengths(String field, int length, int expected, String reason)
    {
        Map<String, Object> request = registration();
        request.put(field, valueWithLength(field, length));
        var response = given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users").then()
                .statusCode(expected);
        if (expected == 400)
        {
            response.contentType("application/problem+json").body("status", is(400));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM users", Long.class));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM user_profiles", Long.class));
        } else
        {
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM users", Long.class));
        }
    }

    @Test
    @DisplayName("Register - duplicate normalized email is mapped to a conflict ProblemDetail")
    void duplicateEmailMapping()
    {
        Map<String, Object> request = registration();
        given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users").then().statusCode(201)
                .body("role", equalTo("CUSTOMER"));
        request.put("email", "CUSTOMER@EXAMPLE.TEST");
        given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users").then().statusCode(409)
                .contentType("application/problem+json");
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/user/user-controller/register-role.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Register - client-supplied roles never grant additional privileges")
    void registerRole(String role, int expected, String reason)
    {
        Map<String, Object> request = registration();
        request.put("role", role);
        given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users").then().statusCode(expected)
                .body("role", equalTo("CUSTOMER"));
        assertEquals("CUSTOMER",
                jdbc.queryForObject("SELECT r.name FROM users u JOIN roles r ON r.id = u.role_id WHERE u.email = ?",
                        String.class, "customer@example.test"));
    }

    @Test
    @DisplayName("Login - credential failures are translated to a generic unauthorized ProblemDetail")
    void loginFailureMapping()
    {
        given().port(port).contentType(ContentType.JSON).body(registration()).post("/v1/users").then().statusCode(201);
        given().port(port).contentType(ContentType.JSON)
                .body(Map.of("email", "customer@example.test", "password", "Incorrect123!")).post("/v1/users/login")
                .then().statusCode(401).contentType("application/problem+json").body("status", is(401))
                .body("detail", equalTo("Invalid email or password"));
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/user/user-controller/login-fields.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Login - malformed credentials are rejected before account authentication")
    void loginFields(String field, String value, int expected, String reason)
    {
        Map<String, Object> request = new HashMap<>();
        request.put("email", "customer@example.test");
        request.put("password", "Customer123!");
        request.put(field, value);
        given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users/login").then()
                .statusCode(expected).contentType("application/problem+json").body("status", is(expected));
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/user/user-controller/login-lengths.csv", numLinesToSkip = 1)
    @DisplayName("Login - distinguishes oversized credentials from well-formed but unknown credentials")
    void loginLengths(String field, int length, int expected, String reason)
    {
        Map<String, Object> request = new HashMap<>();
        request.put("email", "unknown@example.test");
        request.put("password", "Customer123!");
        request.put(field, valueWithLength(field, length));
        given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users/login").then()
                .statusCode(expected).contentType("application/problem+json").body("status", is(expected));
    }

    @Test
    @DisplayName("Current profile - the authenticated identity cannot be replaced by another user's query parameter")
    void currentProfileIsolation()
    {
        Map<String, Object> request = registration();
        int firstId = given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users").then()
                .statusCode(201).extract().path("id");
        request.put("email", "other@example.test");
        request.put("firstName", "Other");
        int otherId = given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users").then()
                .statusCode(201).extract().path("id");
        given().port(port).auth().oauth2(login("CUSTOMER")).queryParam("userId", otherId).get("/v1/users/me").then()
                .statusCode(200).header("Cache-Control", containsString("no-store")).body("id", equalTo(firstId))
                .body("profile.firstName", equalTo("Jonas")).body("passwordHash", nullValue())
                .body("password", nullValue()).body("sessionVersion", nullValue());
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/user/user-controller/role-access.csv", numLinesToSkip = 1)
    @DisplayName("Role access - accounts only enter their assigned API scope")
    void roleAccess(String role, String path, int expected, String reason)
    {
        Map<String, Object> request = registration();
        String email = emailForRole(role);
        request.put("email", email);
        given().port(port).contentType(ContentType.JSON).body(request).post("/v1/users").then().statusCode(201);
        // Roles other than CUSTOMER are test fixtures, not publicly provisioned
        // accounts.
        jdbc.update("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = ?) WHERE email = ?", role, email);
        String token = login(role);
        given().port(port).auth().oauth2(token).get("/v1/users/me").then().statusCode(200).body("role", equalTo(role));
        given().port(port).auth().oauth2(token).get(path).then().statusCode(expected);
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/user/user-controller/invalid-tokens.csv", numLinesToSkip = 1)
    @DisplayName("Token validation - rejects expired, tampered, missing-claim, malformed and disabled-user tokens")
    void invalidTokens(String kind, int expected, String reason)
    {
        given().port(port).contentType(ContentType.JSON).body(registration()).post("/v1/users").then().statusCode(201);
        String token = login("CUSTOMER");
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'customer@example.test'", Long.class);
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode("0123456789abcdef0123456789abcdef0123456789abcdef"));
        if (kind.equals("expired"))
        {
            token = Jwts.builder().issuer("mtogo").subject(userId.toString()).claim("sessionVersion", 0)
                    .expiration(java.util.Date.from(java.time.Instant.now().minusSeconds(60))).signWith(key).compact();
        } else if (kind.equals("missingClaims"))
        {
            token = Jwts.builder().issuer("mtogo").subject(userId.toString()).signWith(key).compact();
        } else if (kind.equals("tampered"))
        {
            token = "X" + token.substring(1);
        } else if (kind.equals("malformed"))
        {
            token = "invalid";
        } else if (kind.equals("inactive"))
        {
            jdbc.update("UPDATE users SET is_active = false WHERE id = ?", userId);
        }
        given().port(port).auth().oauth2(token).get("/v1/users/me").then().statusCode(expected)
                .contentType("application/problem+json");
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/user/user-controller/account-access.csv", numLinesToSkip = 1)
    @DisplayName("Account access - anonymous and unsupported requests cannot access private account endpoints")
    void accountAccess(String method, String path, int expected, String reason)
    {
        given().port(port).when().request(method, path).then().statusCode(expected);
    }

    @Test
    @DisplayName("API contract - login request and authentication response have distinct schemas")
    void authenticationContract()
    {
        given().port(port).get("/v3/api-docs").then().statusCode(200)
                .body("components.schemas.LoginUserDTO.properties.password", notNullValue())
                .body("components.schemas.AuthenticatedUserDTO.properties.accessToken", notNullValue())
                .body("components.schemas.AuthenticatedUserDTO.properties.password", nullValue());
    }

    private static String valueWithLength(String field, int length)
    {
        if (!field.equals("email"))
        {
            return "a".repeat(length);
        }
        // Keep each email label valid so only the total-length boundary is tested.
        String prefix = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + ".";
        String suffix = ".test";
        return prefix + "d".repeat(length - prefix.length() - suffix.length()) + suffix;
    }

    private String login(String role)
    {
        String email = emailForRole(role);
        String password = "Customer123!";
        return given().port(port).contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
                .post("/v1/users/login").then().statusCode(200).body("tokenType", equalTo("Bearer")).extract()
                .path("accessToken");
    }

    private static String emailForRole(String role)
    {
        return switch (role)
        {
            case "RESTAURANT" -> "restaurant@example.test";
            case "DELIVERY_AGENT" -> "agent@example.test";
            default -> "customer@example.test";
        };
    }

    private static Map<String, Object> registration()
    {
        Map<String, Object> request = new HashMap<>();
        request.put("firstName", "Jonas");
        request.put("lastName", "Tester");
        request.put("email", "customer@example.test");
        request.put("password", "Customer123!");
        request.put("confirmPassword", "Customer123!");
        request.put("phoneNumber", "12345678");
        request.put("postalCode", "1000");
        request.put("city", "Copenhagen");
        request.put("country", "Denmark");
        return request;
    }
}
