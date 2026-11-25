package mtogo.feature.order.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.domain.OrderStatus;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"FEATURE_AUTH_ENABLED=true",
        "FEATURE_ORDERING_ENABLED=true", "JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef"})
class RestaurantOrderControllerTest extends OrderTestData
{
    private static final String ORDERS_PATH = "/v1/restaurant/orders";

    @LocalServerPort
    int port;
    @Autowired
    JwtService tokens;
    @Autowired
    OrderService orders;

    @Test
    @DisplayName("Get all - lists only the restaurant's orders, newest first, with their lines")
    void getAllReturnsOwnOrders()
    {
        long customerId = customer();
        long restaurantId = restaurant("Kebab restaurant", true);
        long requesterId = restaurantUser(restaurantId);
        long otherRestaurantId = restaurant("Bakery", true);
        long itemId = item(menu(restaurantId, "Lunch", true), "Kebab", "89.95");
        long firstOrderId = place(customerId, itemId);
        place(customerId, item(menu(otherRestaurantId, "Bread", true), "Bun", true));
        long latestOrderId = place(customerId, itemId);

        request(port, requesterId).get(ORDERS_PATH).then().statusCode(200)
                .body("id", contains((int) latestOrderId, (int) firstOrderId)).body("[0].status", is("PLACED"))
                .body("[0].statusName.danish", is("Bestilt")).body("[0].statusName.english", is("Placed"))
                .body("[0].total", is(179.90f)).body("[0].lines[0].name", is("Kebab"))
                .body("[0].lines[0].quantity", is(2));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/restaurant-order-controller/get-all-access.csv", numLinesToSkip = 1)
    @DisplayName("Get all - only restaurant accounts can list their orders")
    void getAllAccess(String role, int expected, String reason)
    {
        RequestSpecification request = switch (role)
        {
            case "ANONYMOUS" -> given().port(port);
            case "RESTAURANT" -> request(port, restaurantUser(restaurant("Empty restaurant", true)));
            default -> request(port, user("requester@example.test", role));
        };

        var response = request.get(ORDERS_PATH).then().statusCode(expected);
        if (expected == 200)
        {
            response.body("size()", is(0));
        } else
        {
            response.contentType("application/problem+json").body("status", is(expected));
        }
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/restaurant-order-controller/update-status-fields.csv", numLinesToSkip = 1)
    @DisplayName("Update status - accepts only a status a restaurant sets and records it")
    void updateStatusFields(String status, int expected, String reason)
    {
        long restaurantId = restaurant("Kebab restaurant", true);
        long requesterId = restaurantUser(restaurantId);
        long orderId = place(customer(), item(menu(restaurantId, "Lunch", true), "Kebab", true));
        String body = switch (status)
        {
            case "MISSING" -> "{}";
            // 1 is the position of ACCEPTED, so a number read as a position would succeed.
            case "NUMERIC" -> "{\"status\":1}";
            default -> "{\"status\":\"" + status + "\"}";
        };

        var response = patch(request(port, requesterId), orderId, body).then().statusCode(expected);
        if (expected == 200)
        {
            OrderStatus requested = OrderStatus.valueOf(status);
            response.body("status", is(status)).body("statusName.danish", is(requested.getDanishName()))
                    .body("statusName.english", is(requested.getEnglishName()));
            assertEquals(status, readStatus(orderId));
            assertEquals(List.of("PLACED", status), orderHistory(orderId));
        } else
        {
            response.contentType("application/problem+json").body("status", is(expected));
            assertEquals("PLACED", readStatus(orderId));
            assertEquals(List.of("PLACED"), orderHistory(orderId));
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/restaurant-order-controller/update-status-access.csv", numLinesToSkip = 1)
    @DisplayName("Update status - other restaurants, non-restaurant roles and missing orders are refused")
    void updateStatusAccess(String role, String order, int expected, String reason)
    {
        long restaurantId = restaurant("Kebab restaurant", true);
        long orderId = place(customer(), item(menu(restaurantId, "Lunch", true), "Kebab", true));
        RequestSpecification request = switch (role)
        {
            case "ANONYMOUS" -> given().port(port);
            case "RESTAURANT" ->
                request(port, restaurantUser(order.equals("OTHER") ? restaurant("Bakery", true) : restaurantId));
            default -> request(port, user("requester@example.test", role));
        };
        String requestedId = switch (order)
        {
            case "UNKNOWN" -> Long.toString(orderId + 1);
            case "INVALID" -> "invalid";
            default -> Long.toString(orderId);
        };

        patch(request, requestedId, "{\"status\":\"ACCEPTED\"}").then().statusCode(expected)
                .contentType("application/problem+json").body("status", is(expected));
        assertEquals("PLACED", readStatus(orderId));
        assertEquals(List.of("PLACED"), orderHistory(orderId));
    }

    private long restaurantUser(long restaurantId)
    {
        return restaurantUser("restaurant@example.test", restaurantId);
    }

    private long place(long customerId, long itemId)
    {
        return orders
                .place(customerId,
                        new PlaceOrderDTO(List.of(new PlaceOrderDTO.OrderLine(itemId, 2)),
                                new PlaceOrderDTO.DeliveryAddress("Test Street 10", "1165", "Copenhagen"), "tok_visa"))
                .id();
    }

    private RequestSpecification request(int serverPort, long userId)
    {
        return given().port(serverPort).auth().oauth2(tokens.issue(userId, 0).value());
    }

    private Response patch(RequestSpecification request, Object orderId, String body)
    {
        return request.contentType("application/json").body(body).patch(ORDERS_PATH + "/{id}", orderId);
    }

    @Nested
    @TestPropertySource(properties = "FEATURE_ORDERING_ENABLED=false")
    class WhenOrderingIsDisabled
    {
        @LocalServerPort
        int disabledPort;

        @ParameterizedTest(name = "[{index}] {3}")
        @CsvFileSource(resources = "/testcases/order/restaurant-order-controller/get-all-and-update-status-disabled.csv", numLinesToSkip = 1)
        @DisplayName("Restaurant orders - both endpoints are hidden before authentication when ordering is disabled")
        void endpointsAreNotFound(String method, String path, int expected, String reason)
        {
            given().port(disabledPort).request(method, path).then().statusCode(expected)
                    .contentType("application/problem+json").body("detail", is("Ordering is not available"));
        }
    }
}
