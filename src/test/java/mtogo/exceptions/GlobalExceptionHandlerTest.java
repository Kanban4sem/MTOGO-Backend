package mtogo.exceptions;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.List;
import mtogo.testsupport.SharedDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SharedDatabase
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest
{
    @LocalServerPort
    int port;

    @Test
    @DisplayName("Api exception - is logged once as a warning and still answered with ProblemDetail")
    void apiExceptionIsLoggedOnceAsWarning(CapturedOutput output)
    {
        given().port(port).get("/v1/restaurants/{id}/menus", -1).then().statusCode(404)
                .contentType("application/problem+json").body("detail", equalTo("Restaurant not found with id: -1"))
                .body("instance", equalTo("/v1/restaurants/-1/menus"));

        List<String> logLines = output.getOut().lines()
                .filter(line -> line.contains("Restaurant not found with id: -1")).toList();
        assertThat(logLines, hasSize(1));
        assertThat(logLines.getFirst(), containsString("WARN"));
    }
}
