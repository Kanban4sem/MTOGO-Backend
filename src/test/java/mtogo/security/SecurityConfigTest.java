package mtogo.security;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityConfigTest
{
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    int port;

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/security/security-config/endpoint-access.csv", numLinesToSkip = 1)
    @DisplayName("Endpoint access - only the health check is public")
    void onlyHealthCheckIsPublic(String path, int expected, String reason)
    {
        given().port(port).when().get(path).then().statusCode(expected);
    }
}
