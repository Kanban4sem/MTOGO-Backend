package mtogo.feature.maintenance.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.List;
import java.util.Map;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import mtogo.feature.order.OrderTestData;
import mtogo.security.JwtService;
import mtogo.testsupport.AuthenticatedEndpointTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

@AuthenticatedEndpointTest
class MaintenanceControllerTest extends OrderTestData
{
    private static final String PATH = "/v1/maintenance";

    @LocalServerPort
    int port;
    @Autowired
    JwtService tokens;

    @BeforeEach
    void resumeFeatures()
    {
        jdbc.update("UPDATE feature_maintenance SET is_paused = false");
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/maintenance/maintenance-controller/put-access.csv", numLinesToSkip = 1)
    @DisplayName("Set pause - only an authenticated admin can change maintenance status")
    void setPauseAccess(String role, int expected, String reason)
    {
        RequestSpecification requester = role.equals("ANONYMOUS")
                ? given().port(port)
                : request(user("requester@example.test", role));

        requester.contentType(ContentType.JSON).body(Map.of("paused", true)).put(PATH + "/ORDERING").then()
                .statusCode(expected);

        given().port(port).get(PATH).then().statusCode(200).body("find { it.feature == 'ORDERING' }.paused",
                is(expected == 204));
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/maintenance/maintenance-controller/put-fields.csv", numLinesToSkip = 1)
    @DisplayName("Set pause - requires a supported feature and a pause state")
    void setPauseFields(String feature, String body, int expected, String reason)
    {
        request(user("admin@example.test", "ADMIN")).contentType(ContentType.JSON).body(body)
                .put(PATH + "/{feature}", feature).then().statusCode(expected).contentType("application/problem+json")
                .body("status", is(expected));
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/maintenance/maintenance-controller/post-paused-endpoint.csv", numLinesToSkip = 1)
    @DisplayName("Pause and resume - the selected endpoint returns a problem while its feature is paused")
    void pauseAndResume(String feature, String path, String otherFeature, int expected, String reason)
    {
        long admin = user("admin@example.test", "ADMIN");
        String customerToken = tokens.issue(customer(), 0).value();
        String featureStatus = "find { it.feature == '" + feature + "' }";
        String otherStatus = "find { it.feature == '" + otherFeature + "' }";
        given().port(port).get(PATH).then().statusCode(200).header("Cache-Control", "no-store")
                .body("feature", containsInAnyOrder("ORDERING", "AUTHENTICATION"))
                .body(featureStatus + ".paused", is(false)).body(featureStatus + ".message", nullValue());

        setPause(admin, feature, true);
        String message = given().port(port).get(PATH).then().statusCode(200).body(featureStatus + ".paused", is(true))
                .body(otherStatus + ".paused", is(false))
                .body(featureStatus + ".message", containsString("temporarily unavailable")).extract()
                .path(featureStatus + ".message");
        given().port(port).contentType(ContentType.JSON).body("{}").post(path).then().statusCode(expected)
                .contentType("application/problem+json").body("status", is(expected)).body("detail", is(message));
        given().port(port).auth().oauth2(customerToken).get("/v1/users/me").then().statusCode(200);

        setPause(admin, feature, false);
        given().port(port).get(PATH).then().statusCode(200).body(featureStatus + ".paused", is(false));
        given().port(port).auth().oauth2(customerToken).contentType(ContentType.JSON).body("{}").post(path).then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Authentication pause - a database operator can reopen login without an admin token")
    void resumeAuthenticationThroughDatabase()
    {
        setPause(user("admin@example.test", "ADMIN"), "AUTHENTICATION", true);

        jdbc.update("UPDATE feature_maintenance SET is_paused = false WHERE feature = 'AUTHENTICATION'");

        given().port(port).get(PATH).then().statusCode(200).body("find { it.feature == 'AUTHENTICATION' }.paused",
                is(false));
        given().port(port).contentType(ContentType.JSON).body("{}").post("/v1/users/login").then().statusCode(400);
    }

    @Test
    @DisplayName("Ordering pause - the restaurant and delivery agent can still complete an existing order")
    void completeExistingOrder()
    {
        long restaurantId = restaurant("Bakery", true);
        long restaurantManager = restaurantManager("restaurant@example.test", restaurantId);
        long deliveryAgent = deliveryAgent("agent");
        long itemId = item(menu(restaurantId, "Lunch", true), "Sandwich", true);
        int orderId = request(customer()).contentType(ContentType.JSON).body(placeOrder(List.of(line(itemId, 1))))
                .post("/v1/orders").then().statusCode(201).extract().path("id");

        setPause(user("admin@example.test", "ADMIN"), "ORDERING", true);

        updateOrder(restaurantManager, "/v1/restaurant/orders/{id}", orderId, "status", "ACCEPTED", "ACCEPTED");
        updateOrder(restaurantManager, "/v1/restaurant/orders/{id}", orderId, "status", "READY", "READY");
        updateOrder(deliveryAgent, "/v1/delivery-agent/orders/{id}", orderId, "action", "TAKE", "READY");
        updateOrder(deliveryAgent, "/v1/delivery-agent/orders/{id}", orderId, "action", "PICK_UP", "PICKED_UP");
        updateOrder(deliveryAgent, "/v1/delivery-agent/orders/{id}", orderId, "action", "DELIVER", "DELIVERED");
    }

    private void updateOrder(long userId, String path, int orderId, String field, String value, String status)
    {
        request(userId).contentType(ContentType.JSON).body(Map.of(field, value)).patch(path, orderId).then()
                .statusCode(200).body("status", is(status));
    }

    private void setPause(long admin, String feature, boolean isPaused)
    {
        request(admin).contentType(ContentType.JSON).body(Map.of("paused", isPaused)).put(PATH + "/{feature}", feature)
                .then().statusCode(204);
    }

    private RequestSpecification request(long userId)
    {
        return given().port(port).auth().oauth2(tokens.issue(userId, 0).value());
    }
}
