package mtogo.feature.featureflag.presentation;

import mtogo.testsupport.SharedDatabase;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

// The flag is pinned so a developer's local .env cannot change the result. AppPropertiesTest covers the unset default.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "FEATURE_AUTH_ENABLED=false")
@SharedDatabase
class FeatureFlagControllerTest
{
    @LocalServerPort
    int port;

    @Test
    @DisplayName("Get all - returns every flag with its value and is never cached")
    void getAllReturnsEveryFlagUncached()
    {
        given().port(port).when().get("/v1/features").then().statusCode(200).header("Cache-Control", "no-store")
                .body("authEnabled", is(false)).body("keySet()", contains("authEnabled"));
    }
}
