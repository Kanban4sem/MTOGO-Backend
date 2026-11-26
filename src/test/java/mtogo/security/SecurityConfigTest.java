package mtogo.security;

import mtogo.testsupport.SharedDatabase;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SharedDatabase
// Tests turn metrics export off, which removes the Prometheus endpoint.
@AutoConfigureMetrics
class SecurityConfigTest
{
    @LocalServerPort
    int port;
    @LocalManagementPort
    int managementPort;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtService tokens;

    @BeforeEach
    void cleanDatabase()
    {
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM user_profiles");
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/security/security-config/request-endpoint-access.csv", numLinesToSkip = 1)
    @DisplayName("Endpoint access - only the feature flags and the API documentation are public on the API port")
    void onlyFeatureFlagsAndApiDocumentationArePublic(String method, String path, int expected, String reason)
    {
        given().port(port).when().request(method, path).then().statusCode(expected);
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/security/security-config/request-management-access.csv", numLinesToSkip = 1)
    @DisplayName("Management access - health and metrics are public on the management port and absent from the API port")
    void healthAndMetricsArePublicOnlyOnTheManagementPort(String portName, String path, String token, int expected,
            String reason)
    {
        RequestSpecification request = given().port(portName.equals("MANAGEMENT") ? managementPort : port);
        if (token.equals("ADMIN"))
        {
            request = request.auth().oauth2(tokens.issue(admin(), 0).value());
        }

        var response = request.when().get(path).then().statusCode(expected);
        if (portName.equals("SERVER"))
        {
            response.body(not(containsString("jvm_memory")));
        }
    }

    private long admin()
    {
        long profileId = jdbc.queryForObject(
                "INSERT INTO user_profiles (first_name, last_name) VALUES ('Security', 'Tester') RETURNING id",
                Long.class);
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, role_id, profile_id)
                VALUES ('admin@example.test', 'not-used', (SELECT id FROM roles WHERE name = 'ADMIN'), ?) RETURNING id
                """, Long.class, profileId);
    }
}
