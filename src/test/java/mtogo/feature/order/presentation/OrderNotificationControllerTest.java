package mtogo.feature.order.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import io.restassured.specification.RequestSpecification;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.domain.DeliveryAction;
import mtogo.feature.order.domain.ISmsProvider;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.domain.OrderStatus;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateDeliveryAgentOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateRestaurantOrderStatusDTO;
import mtogo.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"FEATURE_AUTH_ENABLED=true",
        "FEATURE_ORDERING_ENABLED=true", "JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef"})
class OrderNotificationControllerTest extends OrderTestData
{
    private static final String NOTIFICATIONS = "/v1/customer/orders/notifications";
    private static final String READ = NOTIFICATIONS + "/{id}/read";

    @LocalServerPort
    int port;
    @Autowired
    JwtService tokens;
    @Autowired
    OrderService orders;
    @Autowired
    TransactionTemplate transactions;
    @MockitoBean
    ISmsProvider sms;

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/order-notification-controller/get-all-status-changes.csv", numLinesToSkip = 1)
    @DisplayName("List notifications - every completed transition is visible to the order's customer")
    void getAllStatusChanges(boolean isRejected, String statuses, int expected, String reason)
    {
        long customerId = customer();
        long otherCustomerId = user("other@example.test", "CUSTOMER");
        long restaurantId = restaurant("Lunch restaurant", true);
        long restaurantUserId = restaurantUser("lunch@example.test", restaurantId);
        long itemId = item(menu(restaurantId, "Lunch", true), "Kebab", true);
        long orderId = place(customerId, itemId);
        long otherOrderId = place(otherCustomerId, itemId);

        request(customerId).get(NOTIFICATIONS).then().statusCode(expected).body("size()", is(0));
        changeStatus(restaurantUserId, otherOrderId, OrderStatus.ACCEPTED);
        if (isRejected)
        {
            changeStatus(restaurantUserId, orderId, OrderStatus.REJECTED);
        } else
        {
            changeStatus(restaurantUserId, orderId, OrderStatus.ACCEPTED);
            changeStatus(restaurantUserId, orderId, OrderStatus.READY);
            long agentId = deliveryAgent("agent");
            orders.updateDeliveryAgentOrder(agentId, orderId, new UpdateDeliveryAgentOrderDTO(DeliveryAction.TAKE));
            orders.updateDeliveryAgentOrder(agentId, orderId, new UpdateDeliveryAgentOrderDTO(DeliveryAction.PICK_UP));
            orders.updateDeliveryAgentOrder(agentId, orderId, new UpdateDeliveryAgentOrderDTO(DeliveryAction.DELIVER));
        }

        request(restaurantUserId).contentType("application/json").body("{\"status\":\"ACCEPTED\"}")
                .patch("/v1/restaurant/orders/{id}", orderId).then().statusCode(409);

        request(customerId).get(NOTIFICATIONS).then().statusCode(expected)
                .body("status", contains(statuses.split("\\|"))).body("orderId", everyItem(is((int) orderId)))
                .body("read", everyItem(is(false))).body("changedAt", everyItem(notNullValue()))
                .body("[0].statusName.danish", is(isRejected ? "Afvist" : "Leveret"))
                .body("[0].statusName.english", is(isRejected ? "Rejected" : "Delivered"));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/order-notification-controller/mark-read-access.csv", numLinesToSkip = 1)
    @DisplayName("Mark read - only the notification owner can clear it from their unread notifications")
    void markReadAccess(String requester, int expected, String reason)
    {
        long customerId = customer();
        long restaurantId = restaurant("Lunch restaurant", true);
        long restaurantUserId = restaurantUser("lunch@example.test", restaurantId);
        long itemId = item(menu(restaurantId, "Lunch", true), "Kebab", true);
        long orderId = place(customerId, itemId);
        changeStatus(restaurantUserId, orderId, OrderStatus.ACCEPTED);
        changeStatus(restaurantUserId, orderId, OrderStatus.READY);
        int id = request(customerId).get(NOTIFICATIONS).then().statusCode(200).extract().path("[0].id");

        RequestSpecification actor = switch (requester)
        {
            case "OWNER", "UNKNOWN", "INVALID" -> request(customerId);
            case "OTHER" -> request(user("other@example.test", "CUSTOMER"));
            case "RESTAURANT" -> request(restaurantUserId);
            case "DELIVERY_AGENT" -> request(deliveryAgent("agent"));
            default -> given().port(port);
        };
        String requestedId = switch (requester)
        {
            case "UNKNOWN" -> Long.toString(id + 1L);
            case "INVALID" -> "invalid";
            default -> Long.toString(id);
        };
        actor.put(READ, requestedId).then().statusCode(expected);
        if (expected == 204)
        {
            request(customerId).put(READ, requestedId).then().statusCode(204);
        }
        request(customerId).get(NOTIFICATIONS).then().statusCode(200).body("read", contains(expected == 204, false));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/order-notification-controller/get-all-access.csv", numLinesToSkip = 1)
    @DisplayName("List notifications - requires a customer account")
    void getAllAccess(String role, int expected, String reason)
    {
        RequestSpecification actor = role.equals("ANONYMOUS")
                ? given().port(port)
                : request(user("requester@example.test", role));
        actor.get(NOTIFICATIONS).then().statusCode(expected).contentType("application/problem+json");
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/order/order-notification-controller/change-status-sms.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Change status - SMS goes to the customer's number only after commit")
    void changeStatusSms(String phoneNumber, String language, boolean isRolledBack, String expected, String reason)
    {
        long customerId = customer();
        jdbc.update("""
                UPDATE user_profiles SET phone_number = ?, preferred_language = ?
                WHERE id = (SELECT profile_id FROM users WHERE id = ?)
                """, phoneNumber, language, customerId);
        long restaurantId = restaurant("Lunch restaurant", true);
        long restaurantUserId = restaurantUser("lunch@example.test", restaurantId);
        long itemId = item(menu(restaurantId, "Lunch", true), "Kebab", true);
        long orderId = place(customerId, itemId);

        transactions.executeWithoutResult(transaction -> {
            changeStatus(restaurantUserId, orderId, OrderStatus.ACCEPTED);
            verifyNoInteractions(sms);
            if (isRolledBack)
            {
                transaction.setRollbackOnly();
            }
        });

        if (expected == null)
        {
            verifyNoInteractions(sms);
        } else
        {
            verify(sms).send(phoneNumber, expected.formatted(orderId));
            verifyNoMoreInteractions(sms);
        }
        request(customerId).get(NOTIFICATIONS).then().statusCode(200).body("size()", is(isRolledBack ? 0 : 1));
    }

    private RequestSpecification request(long userId)
    {
        return given().port(port).auth().oauth2(tokens.issue(userId, 0).value());
    }

    private long place(long customerId, long itemId)
    {
        return orders.place(customerId, new PlaceOrderDTO(List.of(new PlaceOrderDTO.OrderLine(itemId, 2)),
                new PlaceOrderDTO.DeliveryAddress("Test Street 10", "1165", "Copenhagen"), "tok_visa", false, false))
                .id();
    }

    private void changeStatus(long restaurantUserId, long orderId, OrderStatus status)
    {
        orders.updateRestaurantOrderStatus(restaurantUserId, orderId, new UpdateRestaurantOrderStatusDTO(status));
    }
}
