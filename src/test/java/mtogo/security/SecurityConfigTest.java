package mtogo.security;

import mtogo.testsupport.SharedDatabase;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

import java.util.List;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

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

    // SecurityConfig holds no role rules, so an endpoint without its own
    // @PreAuthorize would be open to every logged-in user. A class-level
    // annotation doesn't count: each method declares it.
    @Test
    @DisplayName("Endpoint access - every endpoint method declares its access with its own @PreAuthorize")
    void everyEndpointMethodDeclaresItsAccess()
    {
        List<String> undeclared = handlerMapping.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getPackageName().startsWith("mtogo."))
                .filter(handler -> !handler.getMethod().isAnnotationPresent(PreAuthorize.class))
                .map(handler -> handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName()).sorted()
                .toList();

        assertThat("Endpoint methods without their own @PreAuthorize", undeclared, empty());
    }
}
