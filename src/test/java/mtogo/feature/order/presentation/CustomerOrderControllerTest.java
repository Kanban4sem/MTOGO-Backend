package mtogo.feature.order.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.restassured.specification.RequestSpecification;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.domain.OrderStatus;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.response.OrderDTO;
import mtogo.security.JwtService;
import mtogo.testsupport.AuthenticatedEndpointTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

@AuthenticatedEndpointTest
class CustomerOrderControllerTest extends OrderTestData
{
    private static final String ORDERS_PATH = "/v1/customer/orders";
    private static final String FEEDBACK_PATH = ORDERS_PATH + "/{id}/feedback";
    private static final String NOTIFICATIONS_PATH = ORDERS_PATH + "/notifications";
    private static final Instant ORDER_TIME = Instant.parse("2026-11-25T12:00:00Z");
    private static final Map<String, Object> VALID_FEEDBACK = Map.of("foodRating", 3, "overallRating", 4,
            "deliveryRating", 5);

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
        setOrder(orderId, status, null);

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

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/customer-order-controller/create-feedback-access.csv", numLinesToSkip = 1)
    @DisplayName("Create feedback - rejected requests return a problem and store no feedback")
    void createFeedbackAccess(String role, String order, int expected, String reason)
    {
        long customerId = customer();
        OrderStatus status = order.equals("UNDELIVERED") ? OrderStatus.PICKED_UP : OrderStatus.DELIVERED;
        long orderId = orderForFeedback(customerId, status);
        String requestedId = switch (order)
        {
            case "OTHER", "UNDELIVERED" -> Long.toString(orderId);
            case "UNKNOWN" -> Long.toString(orderId + 1);
            default -> order;
        };
        RequestSpecification actor = role.equals("OWNER") ? request(port, customerId) : requestForRole(port, role);

        actor.contentType("application/json").body(VALID_FEEDBACK).post(FEEDBACK_PATH, requestedId).then()
                .statusCode(expected).contentType("application/problem+json").body("status", is(expected));
        assertEquals(0, feedbackCount());
    }

    @ParameterizedTest(name = "[{index}] {6}")
    @CsvFileSource(resources = "/testcases/order/customer-order-controller/create-feedback-fields.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Create feedback - validates the boundaries of each rating and the optional text")
    void createFeedbackFields(BigDecimal foodRating, BigDecimal overallRating, BigDecimal deliveryRating,
            Integer titleLength, Integer descriptionLength, int expected, String reason)
    {
        long customerId = customer();
        long orderId = orderForFeedback(customerId, OrderStatus.DELIVERED);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("foodRating", foodRating);
        payload.put("overallRating", overallRating);
        payload.put("deliveryRating", deliveryRating);
        payload.put("title", titleLength == null ? null : "t".repeat(titleLength));
        payload.put("description", descriptionLength == null ? null : "d".repeat(descriptionLength));

        var response = request(port, customerId).contentType("application/json").body(payload)
                .post(FEEDBACK_PATH, orderId).then().statusCode(expected);
        if (expected == 400)
        {
            response.contentType("application/problem+json").body("status", is(expected));
            assertEquals(0, feedbackCount());
        } else
        {
            assertEquals(1, feedbackCount());
        }
    }

    @Test
    @DisplayName("Give feedback - only the delivery notification offers feedback and stops after submission")
    void deliveryNotificationOffersFeedbackUntilSubmitted()
    {
        long customerId = customer();
        long orderId = orderForFeedback(customerId, OrderStatus.DELIVERED);

        request(port, customerId).get(NOTIFICATIONS_PATH).then().statusCode(200)
                .body("status", contains("DELIVERED", "READY")).body("canGiveFeedback", contains(true, false));

        request(port, customerId).contentType("application/json").body(VALID_FEEDBACK).post(FEEDBACK_PATH, orderId)
                .then().statusCode(201);

        request(port, customerId).get(NOTIFICATIONS_PATH).then().statusCode(200)
                .body("status", contains("DELIVERED", "READY")).body("canGiveFeedback", contains(false, false));
    }

    private long orderForFeedback(long customerId, OrderStatus status)
    {
        EnumMap<OrderStatus, Instant> statusTimes = new EnumMap<>(OrderStatus.class);
        statusTimes.put(OrderStatus.READY, ORDER_TIME.minusSeconds(60));
        statusTimes.put(status, ORDER_TIME);
        return orderAt(restaurant("Lunch restaurant", true), customerId, "90.00", statusTimes);
    }

    private long feedbackCount()
    {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_feedback", Long.class);
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
}
