package mtogo.feature.user.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mtogo.config.AppProperties;
import mtogo.exceptions.ApiException;
import mtogo.feature.role.domain.RoleName;
import mtogo.feature.user.presentation.dto.request.RegisterUserDTO;
import mtogo.testsupport.SharedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@SharedDatabase
@ExtendWith(OutputCaptureExtension.class)
class UserSeedServiceTest
{
    private static final Map<RoleName, String> VALID_PASSWORDS = Map.of(RoleName.CUSTOMER, "Customer123!",
            RoleName.RESTAURANT, "Restaurant123!", RoleName.DELIVERY_AGENT, "Agent123!");
    private static final Map<RoleName, List<String>> SEEDED_EMAILS = Map.of(RoleName.CUSTOMER,
            List.of("customer@mtogo.example"), RoleName.RESTAURANT,
            List.of("kebab@mtogo.example", "bakery@mtogo.example"), RoleName.DELIVERY_AGENT,
            List.of("agent@mtogo.example"));
    private static final AppProperties.Seed ALL_PASSWORDS = seedPasswords(VALID_PASSWORDS);

    @Autowired
    UserSeedService seedService;
    @Autowired
    UserService userService;
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

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/user/user-seed-service/seed-roles.csv", numLinesToSkip = 1)
    @DisplayName("Seed - creates the users of each role whose seed password is set")
    void seedCreatesUsersOfRolesWithAPassword(String customerPassword, String restaurantPassword,
            String deliveryAgentPassword, String expected, String reason)
    {
        Map<RoleName, String> states = Map.of(RoleName.CUSTOMER, customerPassword, RoleName.RESTAURANT,
                restaurantPassword, RoleName.DELIVERY_AGENT, deliveryAgentPassword);
        Map<RoleName, String> rolePasswords = new EnumMap<>(RoleName.class);
        List<String> expectedEmails = new ArrayList<>();
        states.forEach((role, state) -> {
            rolePasswords.put(role, password(state, VALID_PASSWORDS.get(role)));
            if (state.equals("SET"))
            {
                expectedEmails.addAll(SEEDED_EMAILS.get(role));
            }
        });

        seedService.seed(seedPasswords(rolePasswords));

        assertThat(expected, is("ACCEPT"));
        assertThat(seededEmails(), containsInAnyOrder(expectedEmails.toArray()));
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/user/user-seed-service/seed-lengths.csv", numLinesToSkip = 1)
    @DisplayName("Seed - applies registration's password rules to each seed password")
    void seedAppliesThePasswordRules(RoleName roleName, String unit, int length, String expected, String reason)
    {
        String password = unit.equals("CHARACTERS")
                ? "a".repeat(length)
                : "é".repeat(length / 2) + (length % 2 == 0 ? "" : "a");
        // The other roles get valid passwords, so a rejected row shows that one invalid
        // password seeds nobody at all.
        Map<RoleName, String> rolePasswords = new EnumMap<>(VALID_PASSWORDS);
        rolePasswords.put(roleName, password);

        if (expected.equals("ACCEPT"))
        {
            seedService.seed(seedPasswords(rolePasswords));
            List<String> hashes = jdbc.queryForList("""
                    SELECT u.password_hash FROM users u JOIN roles r ON r.id = u.role_id WHERE r.name = ?
                    """, String.class, roleName.name());
            assertThat(hashes, hasSize(SEEDED_EMAILS.get(roleName).size()));
            hashes.forEach(hash -> assertThat(passwords.matches(password, hash), is(true)));
        } else
        {
            ApiException exception = assertThrows(ApiException.class,
                    () -> seedService.seed(seedPasswords(rolePasswords)));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(seededEmails(), is(empty()));
        }
    }

    @Test
    @DisplayName("Seed - logs each role it skips because its seed password is not set")
    void seedLogsSkippedRoles(CapturedOutput output)
    {
        seedService.seed(new AppProperties.Seed("", "   ", ""));

        for (RoleName role : RoleName.values())
        {
            assertThat(output.getOut(),
                    containsString("Skipped the seeded " + role + " users, because their seed password is not set"));
        }
        assertThat(seededEmails(), is(empty()));
    }

    @Test
    @DisplayName("Seed - leaves an existing user with a seeded email unchanged and seeds the others")
    void seedLeavesAnExistingUserUnchanged(CapturedOutput output)
    {
        var registered = userService.register(new RegisterUserDTO("Jonas", "Tester", "kebab@mtogo.example",
                "Original123!", "Original123!", "12345678", "en", "1000", "Copenhagen", "Denmark"));
        Map<String, Object> before = persistedUser("kebab@mtogo.example");

        seedService.seed(ALL_PASSWORDS);

        assertThat(persistedUser("kebab@mtogo.example"), is(before));
        assertThat(before.get("role"), is("CUSTOMER"));
        assertThat(before.get("restaurant"), is(nullValue()));
        assertThat(passwords.matches("Original123!", (String) before.get("password_hash")), is(true));
        assertThat(userService.getCurrent(registered.id()), is(registered));
        assertThat(output.getOut(), containsString(
                "Skipped the seeded user kebab@mtogo.example, because a user with that email already exists"));
        assertThat(seededEmails(), containsInAnyOrder("customer@mtogo.example", "kebab@mtogo.example",
                "bakery@mtogo.example", "agent@mtogo.example"));
    }

    @Test
    @DisplayName("Seed - a second run creates no duplicate users and changes none")
    void seedTwiceCreatesNoDuplicates()
    {
        seedService.seed(ALL_PASSWORDS);
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM users ORDER BY id");

        seedService.seed(new AppProperties.Seed("Changed123!", "Changed123!", "Changed123!"));

        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id"), is(before));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profiles", Long.class), is(4L));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM addresses", Long.class), is(1L));
    }

    @Test
    @DisplayName("Seed - persists each seeded user with role, profile, address and restaurant")
    void seedPersistsTheCompleteUsers()
    {
        seedService.seed(ALL_PASSWORDS);

        assertSeededUser("customer@mtogo.example", "CUSTOMER", "Test", "Customer", null);
        assertSeededUser("kebab@mtogo.example", "RESTAURANT", "MTOGO", "Kebab", "MTOGO Kebab");
        assertSeededUser("bakery@mtogo.example", "RESTAURANT", "MTOGO", "Bakery", "MTOGO Bakery");
        assertSeededUser("agent@mtogo.example", "DELIVERY_AGENT", "Delivery", "Agent", null);

        Map<String, Object> address = jdbc.queryForMap("""
                SELECT a.street, a.is_default, pc.code, pc.city, pc.country
                FROM users u JOIN user_profiles p ON p.id = u.profile_id
                JOIN addresses a ON a.id = p.address_id JOIN postal_codes pc ON pc.id = a.postal_code_id
                WHERE u.email = 'customer@mtogo.example'
                """);
        assertThat(address.get("street"), is(nullValue()));
        assertThat(address.get("is_default"), is(true));
        assertThat(address.get("code"), is("2800"));
        assertThat(address.get("city"), is("Kongens Lyngby"));
        assertThat(address.get("country"), is("Denmark"));
    }

    private void assertSeededUser(String email, String role, String firstName, String lastName, String restaurant)
    {
        Map<String, Object> user = persistedUser(email);
        assertThat(user.get("is_active"), is(true));
        assertThat(user.get("role"), is(role));
        assertThat(user.get("first_name"), is(firstName));
        assertThat(user.get("last_name"), is(lastName));
        assertThat(user.get("preferred_language"), is("da"));
        assertThat((String) user.get("phone_number"), not(emptyOrNullString()));
        assertThat(user.get("restaurant"), is(restaurant));
        assertThat(user.get("address_id"), role.equals("CUSTOMER") ? notNullValue() : nullValue());
    }

    private Map<String, Object> persistedUser(String email)
    {
        return jdbc.queryForMap("""
                SELECT u.password_hash, u.is_active, r.name AS role, p.first_name, p.last_name,
                       p.preferred_language, p.phone_number, p.address_id, rs.name AS restaurant
                FROM users u JOIN roles r ON r.id = u.role_id JOIN user_profiles p ON p.id = u.profile_id
                LEFT JOIN restaurants rs ON rs.id = u.restaurant_id
                WHERE u.email = ?
                """, email);
    }

    private List<String> seededEmails()
    {
        return jdbc.queryForList("SELECT email FROM users", String.class);
    }

    private static AppProperties.Seed seedPasswords(Map<RoleName, String> passwords)
    {
        return new AppProperties.Seed(passwords.get(RoleName.CUSTOMER), passwords.get(RoleName.RESTAURANT),
                passwords.get(RoleName.DELIVERY_AGENT));
    }

    // UNSET is what an unset variable binds to, BLANK a variable set to spaces.
    private static String password(String state, String password)
    {
        return switch (state)
        {
            case "UNSET" -> "";
            case "BLANK" -> "   ";
            default -> password;
        };
    }
}
