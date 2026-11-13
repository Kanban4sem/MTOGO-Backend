package mtogo.featureflag.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// The flag is pinned so a developer's local .env cannot change the result. AppPropertiesTest covers the unset default.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "FEATURE_AUTH_ENABLED=false")
@Testcontainers
class FeatureFlagControllerTest
{
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    int port;

    @Test
    @DisplayName("Get all - returns every flag with its value and is never cached")
    void getAllReturnsEveryFlagUncached()
    {
        given().port(port).when().get("/v1/features").then().statusCode(200).header("Cache-Control", "no-store")
                .body("authEnabled", is(false)).body("keySet()", containsInAnyOrder("authEnabled"));
    }
}
