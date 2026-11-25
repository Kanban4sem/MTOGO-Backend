package mtogo.security;

import mtogo.testsupport.SharedDatabase;
import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "FEATURE_AUTH_ENABLED=false")
@SharedDatabase
class SecurityConfigTest
{
    @LocalServerPort
    int port;

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/security/security-config/request-endpoint-access.csv", numLinesToSkip = 1)
    @DisplayName("Endpoint access - only the health check, the feature flags and the API documentation are public")
    void onlyHealthCheckFeatureFlagsAndApiDocumentationArePublic(String method, String path, int expected,
            String reason)
    {
        given().port(port).when().request(method, path).then().statusCode(expected);
    }
}
