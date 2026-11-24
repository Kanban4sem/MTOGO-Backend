package mtogo.feature.order.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.List;
import io.restassured.specification.RequestSpecification;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderDTO;
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
class CustomerOrderControllerTest extends OrderTestData
{
    private static final String ORDERS_PATH = "/v1/customer/orders";

    @LocalServerPort
    int port;
    @Autowired
    JwtService tokens;
    @Autowired
    OrderService orders;

    @Test
    @DisplayName("Get all - only the customer's orders appear, most recently placed first, with their delivery address")
    void getAllReturnsOwnOrders()
    {
        long customerId = customer();
        long otherCustomerId = user("other@example.test", "CUSTOMER");
        long restaurantId = restaurant("Lunch restaurant", true);
        long itemId = item(menu(restaurantId, "Lunch", true), "Kebab", "89.95");
        long firstOrderId = place(customerId, itemId).id();
        place(otherCustomerId, itemId);
        long latestOrderId = place(customerId, itemId, true).id();
        changeProfileStreet(customerId);

        request(port, customerId).get(ORDERS_PATH).then().statusCode(200)
                .body("id", contains((int) latestOrderId, (int) firstOrderId))
                .body("[0].restaurantId", is((int) restaurantId)).body("[0].status", is("PLACED"))
                .body("[0].total", is(179.90f)).body("[0].deliveryAddress.street", is("Test Street 10"))
                .body("[0].deliveryAddress.postalCode", is("1165")).body("[0].deliveryAddress.city", is("Copenhagen"))
                .body("[0].deliveryAddress.country", is("Denmark"));
    }

    @Test
    @DisplayName("Get by id - details include the placed time, total, order line snapshots and the order's own delivery address")
    void getByIdReturnsDetails()
    {
        long customerId = customer();
        long itemId = item(menu(restaurant("Lunch restaurant", true), "Lunch", true), "Kebab", "89.95");
        OrderDTO placed = place(customerId, itemId, true);
        jdbc.update("UPDATE menu_items SET name = 'New name', price = 100 WHERE id = ?", itemId);
        changeProfileStreet(customerId);

        request(port, customerId).get(ORDERS_PATH + "/{id}", placed.id()).then().statusCode(200)
                .body("id", is(placed.id().intValue())).body("restaurantId", is(placed.restaurantId().intValue()))
                .body("placedAt", is(placed.placedAt().toString())).body("total", is(179.90f))
                .body("lines.size()", is(1)).body("lines[0].menuItemId", is((int) itemId))
                .body("lines[0].name", is("Kebab")).body("lines[0].unitPrice", is(89.95f))
                .body("lines[0].quantity", is(2)).body("lines[0].subtotal", is(179.90f))
                .body("deliveryAddress.street", is("Test Street 10")).body("deliveryAddress.postalCode", is("1165"))
                .body("deliveryAddress.city", is("Copenhagen")).body("deliveryAddress.country", is("Denmark"));
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/order/customer-order-controller/get-current-status.csv", numLinesToSkip = 1)
    @DisplayName("Read orders - both endpoints return the current status and its Danish and English names")
    void getCurrentStatus(String status, String danishName, String englishName, int expected, String reason)
    {
        long customerId = customer();
        long itemId = item(menu(restaurant("Lunch restaurant", true), "Lunch", true), "Kebab", true);
        long orderId = place(customerId, itemId).id();
        jdbc.update("UPDATE orders SET status = ? WHERE id = ?", status, orderId);

        request(port, customerId).get(ORDERS_PATH).then().statusCode(expected).body("[0].status", is(status))
                .body("[0].statusName.danish", is(danishName)).body("[0].statusName.english", is(englishName));
        request(port, customerId).get(ORDERS_PATH + "/{id}", orderId).then().statusCode(expected)
                .body("status", is(status)).body("statusName.danish", is(danishName))
                .body("statusName.english", is(englishName));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/customer-order-controller/get-all-access.csv", numLinesToSkip = 1)
    @DisplayName("Get all - only customers can list their orders, including an empty list")
    void getAllAccess(String role, int expected, String reason)
    {
        RequestSpecification request = requestForRole(port, role);

        if (expected == 200)
        {
            request.get(ORDERS_PATH).then().statusCode(expected).body("size()", is(0));
        } else
        {
            request.get(ORDERS_PATH).then().statusCode(expected).contentType("application/problem+json");
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/customer-order-controller/get-by-id-access.csv", numLinesToSkip = 1)
    @DisplayName("Get by id - customers can read their own orders only")
    void getByIdAccess(String role, String order, int expected, String reason)
    {
        long customerId = customer();
        long itemId = item(menu(restaurant("Lunch restaurant", true), "Lunch", true), "Kebab", true);
        long orderId = place(customerId, itemId).id();
        String requestedId = switch (order)
        {
            case "OWN", "OTHER" -> Long.toString(orderId);
            case "UNKNOWN" -> Long.toString(orderId + 1);
            default -> order;
        };
        RequestSpecification request = order.equals("OWN") ? request(port, customerId) : requestForRole(port, role);
        var response = request.get(ORDERS_PATH + "/{id}", requestedId).then().statusCode(expected);

        if (expected != 200)
        {
            response.contentType("application/problem+json").body("status", is(expected));
        }
        if (expected == 404)
        {
            response.body("detail", is("Order not found with id: " + requestedId));
        }
    }

    private OrderDTO place(long customerId, long itemId)
    {
        return place(customerId, itemId, false);
    }

    private OrderDTO place(long customerId, long itemId, boolean saveAsDefault)
    {
        return orders.place(customerId,
                new PlaceOrderDTO(List.of(new PlaceOrderDTO.OrderLine(itemId, 2)),
                        new PlaceOrderDTO.DeliveryAddress("Test Street 10", "1165", "Copenhagen"), "tok_visa", false,
                        saveAsDefault));
    }

    private void changeProfileStreet(long customerId)
    {
        jdbc.update("""
                UPDATE addresses SET street = 'Profile Street 1' WHERE id =
                    (SELECT p.address_id FROM user_profiles p JOIN users u ON u.profile_id = p.id WHERE u.id = ?)
                """, customerId);
    }

    private RequestSpecification requestForRole(int serverPort, String role)
    {
        return role.equals("ANONYMOUS")
                ? given().port(serverPort)
                : request(serverPort, user("requester@example.test", role));
    }

    private RequestSpecification request(int serverPort, long userId)
    {
        return given().port(serverPort).auth().oauth2(tokens.issue(userId, 0).value());
    }

    @Nested
    @TestPropertySource(properties = "FEATURE_ORDERING_ENABLED=false")
    class WhenOrderingIsDisabled
    {
        @LocalServerPort
        int disabledPort;

        @ParameterizedTest(name = "[{index}] {2}")
        @CsvFileSource(resources = "/testcases/order/customer-order-controller/get-disabled.csv", numLinesToSkip = 1)
        @DisplayName("Read orders - with ordering disabled, both endpoints return 404 before authentication")
        void getIsNotFound(String path, int expected, String reason)
        {
            given().port(disabledPort).get(path).then().statusCode(expected).contentType("application/problem+json")
                    .body("detail", is("Ordering is not available"));
        }
    }
}
