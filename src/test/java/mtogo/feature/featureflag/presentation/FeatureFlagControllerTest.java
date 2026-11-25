package mtogo.feature.featureflag.presentation;

import mtogo.testsupport.SharedDatabase;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SharedDatabase
class FeatureFlagControllerTest
{
    @LocalServerPort
    int port;

    @Test
    @DisplayName("Get all - returns an empty object while no flag is declared, and is never cached")
    void getAllReturnsNoFlagsUncached()
    {
        given().port(port).when().get("/v1/features").then().statusCode(200).header("Cache-Control", "no-store")
                .body("keySet()", is(empty()));
    }
}
