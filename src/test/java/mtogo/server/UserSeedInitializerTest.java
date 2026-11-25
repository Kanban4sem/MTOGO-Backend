package mtogo.server;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static mtogo.testsupport.CauseUtil.causeNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.restassured.http.ContentType;
import mtogo.Main;
import mtogo.testsupport.SharedDatabase;
import mtogo.testsupport.SharedPostgres;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"FEATURE_AUTH_ENABLED=true",
        "JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef", "app.seed.customer-password=Customer123!",
        "app.seed.restaurant-password=Restaurant123!", "app.seed.delivery-agent-password=Agent123!",
        "app.seed.admin-password=Admin123!"})
@SharedDatabase
class UserSeedInitializerTest
{
    private static final Map<String, String> SEED_PASSWORDS = Map.of("CUSTOMER", "Customer123!", "RESTAURANT",
            "Restaurant123!", "DELIVERY_AGENT", "Agent123!", "ADMIN", "Admin123!");

    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Startup - seeds the users of every role whose seed password is set")
    void startupSeedsTheUsers()
    {
        assertThat(jdbc.queryForList("SELECT email FROM users", String.class),
                containsInAnyOrder("customer@mtogo.example", "kebab@mtogo.example", "bakery@mtogo.example",
                        "agent@mtogo.example", "admin@mtogo.example"));
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/server/user-seed-initializer/login-seeded-users.csv", numLinesToSkip = 1)
    @DisplayName("Login - a seeded user logs in with their role's seed password and gets that role")
    void seededUserLogsIn(String email, String passwordRole, int expected, String reason)
    {
        var response = given().port(port).contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", SEED_PASSWORDS.get(passwordRole))).post("/v1/users/login")
                .then().statusCode(expected);
        if (expected == 200)
        {
            String token = response.extract().path("accessToken");
            given().port(port).auth().oauth2(token).get("/v1/users/me").then().statusCode(200)
                    .body("email", equalTo(email)).body("role", equalTo(passwordRole));
        }
    }

    @Test
    @DisplayName("Startup - seeds the users with the authentication flag off")
    void startupSeedsTheUsersWithAuthenticationOff()
    {
        try (ConfigurableApplicationContext context = startWithAuthenticationOff("test_user_seed_auth_off",
                "Customer123!"))
        {
            JdbcTemplate startedJdbc = context.getBean(JdbcTemplate.class);
            assertThat(startedJdbc.queryForList("SELECT email FROM users", String.class),
                    contains("customer@mtogo.example"));
        }
    }

    @Test
    @DisplayName("Startup - an invalid seed password stops the backend")
    void invalidSeedPasswordStopsStartup()
    {
        Exception exception = assertThrows(Exception.class,
                () -> startWithAuthenticationOff("test_user_seed_invalid_password", "short"));

        assertThat(causeNames(exception), hasItem("BadRequestException"));
    }

    // Starts a second backend on its own database, with only the customer seed
    // password set.
    private static ConfigurableApplicationContext startWithAuthenticationOff(String database, String customerPassword)
    {
        List<String> arguments = new ArrayList<>();
        SharedPostgres.datasourceProperties(database).forEach(property -> arguments.add("--" + property));
        arguments.add("--app.seed.customer-password=" + customerPassword);
        arguments.add("--FEATURE_AUTH_ENABLED=false");
        arguments.add("--server.port=0");
        return new SpringApplicationBuilder(Main.class).run(arguments.toArray(String[]::new));
    }
}
