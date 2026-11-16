package mtogo.feature.restaurant.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import mtogo.feature.restaurant.RestaurantTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "FEATURE_AUTH_ENABLED=false")
class RestaurantControllerTest extends RestaurantTestData
{
    @LocalServerPort
    int port;

    @Test
    @DisplayName("Browse - anonymous clients receive restaurant and nested menu JSON with decimal prices")
    void anonymousContract()
    {
        long id = restaurant("Public restaurant", true);
        long menuId = menu(id, "Lunch", true);
        long itemId = item(menuId, "Meal", true);
        given().port(port).get("/v1/restaurants").then().statusCode(200).contentType("application/json")
                .body("size()", is(1)).body("[0].id", equalTo((int) id)).body("[0].name", equalTo("Public restaurant"))
                .body("[0].acceptsOrders", is(true));
        given().port(port).get("/v1/restaurants/{id}/menus", id).then().statusCode(200).contentType("application/json")
                .body("size()", is(1)).body("[0].id", equalTo((int) menuId)).body("[0].restaurantId", equalTo((int) id))
                .body("[0].name", equalTo("Lunch")).body("[0].description", equalTo("Menu description"))
                .body("[0].items.size()", is(1)).body("[0].items[0].id", equalTo((int) itemId))
                .body("[0].items[0].name", equalTo("Meal"))
                .body("[0].items[0].description", equalTo("Item description"))
                .body("[0].items[0].price", equalTo(89.95f)).body("[0].items[0].imageUrl", equalTo("/images/meal.jpg"))
                .body("[0].items[0].available", is(true));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/restaurant/restaurant-controller/get-menus-path.csv", numLinesToSkip = 1)
    @DisplayName("Get menus - invalid IDs and unavailable restaurants produce ProblemDetail")
    void getMenusPath(String restaurantId, int expected, String reason)
    {
        long closed = restaurant("Closed", false);
        String pathId = restaurantId.equals("closed") ? Long.toString(closed) : restaurantId;
        given().port(port).get("/v1/restaurants/{id}/menus", pathId).then().statusCode(expected)
                .contentType("application/problem+json").body("status", is(expected))
                .body("detail", not(blankOrNullString()))
                .body("instance", equalTo("/v1/restaurants/" + pathId + "/menus"));
    }
}
