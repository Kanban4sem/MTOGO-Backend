package mtogo.feature.order.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateRestaurantOrderStatusDTO;
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
class DeliveryAgentOrderControllerTest extends OrderTestData
{
    private static final String PATH = "/v1/delivery-agent/orders";

    @LocalServerPort
    int port;
    @Autowired
    JwtService tokens;
    @Autowired
    OrderService orders;

    @Test
    @DisplayName("Delivery - a ready order can be taken, picked up and delivered by one agent")
    void deliverReadyOrder()
    {
        long restaurantId = restaurant("Bakery", true);
        long restaurantUser = user("restaurant@example.test", "RESTAURANT");
        jdbc.update("UPDATE users SET restaurant_id = ? WHERE id = ?", restaurantId, restaurantUser);
        long agent = agent("first");
        long otherAgent = agent("second");
        long orderId = place(restaurantId);
        orders.updateRestaurantOrderStatus(restaurantUser, orderId, new UpdateRestaurantOrderStatusDTO("ACCEPTED"));
        request(agent).get(PATH).then().statusCode(200).body("size()", is(0));

        orders.updateRestaurantOrderStatus(restaurantUser, orderId, new UpdateRestaurantOrderStatusDTO("READY"));
        request(agent).get(PATH).then().statusCode(200).body("id", contains((int) orderId))
                .body("[0].status", is("READY")).body("[0].assignedToMe", is(false))
                .body("[0].restaurantName", is("Bakery")).body("[0].deliveryCity", is("Copenhagen"))
                .body("[0].deliveryAddress.street", is("Test Street 10"))
                .body("[0].deliveryAddress.postalCode", is("1165")).body("[0].deliveryAddress.city", is("Copenhagen"))
                .body("[0].deliveryAddress.country", is("Denmark"));

        patch(request(agent), orderId, "TAKE").then().statusCode(200).body("status", is("READY"))
                .body("assignedToMe", is(true)).body("deliveryAddress.street", is("Test Street 10"));
        request(otherAgent).get(PATH).then().statusCode(200).body("size()", is(0));
        patch(request(otherAgent), orderId, "TAKE").then().statusCode(409);
        patch(request(otherAgent), orderId, "PICK_UP").then().statusCode(404);
        patch(request(agent), orderId, "DELIVER").then().statusCode(409);
        patch(request(agent), orderId, "PICK_UP").then().statusCode(200).body("status", is("PICKED_UP"));
        request(agent).get(PATH).then().statusCode(200).body("[0].status", is("PICKED_UP"));
        patch(request(otherAgent), orderId, "DELIVER").then().statusCode(404);
        patch(request(agent), orderId, "DELIVER").then().statusCode(200).body("status", is("DELIVERED"));
        patch(request(agent), orderId, "DELIVER").then().statusCode(409);
        request(agent).get(PATH).then().statusCode(200).body("[0].status", is("DELIVERED"));
        request(otherAgent).get(PATH).then().statusCode(200).body("size()", is(0));

        assertEquals(agent,
                jdbc.queryForObject("SELECT delivery_agent_id FROM orders WHERE id = ?", Long.class, orderId));
        assertEquals(List.of("PLACED", "ACCEPTED", "READY", "PICKED_UP", "DELIVERED"), jdbc.queryForList(
                "SELECT status FROM order_status_changes WHERE order_id = ? ORDER BY id", String.class, orderId));
    }

    @Test
    @DisplayName("Take - simultaneous requests can assign an order to only one agent")
    void takeConcurrently() throws Exception
    {
        long orderId = place(restaurant("Bakery", true));
        jdbc.update("UPDATE orders SET status = 'READY' WHERE id = ?", orderId);
        long firstAgent = agent("first");
        long secondAgent = agent("second");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2))
        {
            var first = executor.submit(() -> takeWhenStarted(firstAgent, orderId, ready, start));
            var second = executor.submit(() -> takeWhenStarted(secondAgent, orderId, ready, start));
            boolean bothReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(bothReady);
            int firstStatus = first.get(20, TimeUnit.SECONDS);
            int secondStatus = second.get(20, TimeUnit.SECONDS);
            assertEquals(List.of(200, 409), List.of(firstStatus, secondStatus).stream().sorted().toList());
            long winner = firstStatus == 200 ? firstAgent : secondAgent;
            assertEquals(winner,
                    jdbc.queryForObject("SELECT delivery_agent_id FROM orders WHERE id = ?", Long.class, orderId));
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/delivery-agent-order-controller/access.csv", numLinesToSkip = 1)
    @DisplayName("Delivery endpoints - enforce role access and map invalid requests to problem details")
    void access(String role, String operation, int expected, String reason)
    {
        long orderId = place(restaurant("Bakery", true));
        RequestSpecification requester = switch (role)
        {
            case "ANONYMOUS" -> given().port(port);
            default -> request(user("requester@example.test", role));
        };
        Response result = switch (operation)
        {
            case "LIST" -> requester.get(PATH);
            case "UNKNOWN" -> patch(requester, orderId + 1, "TAKE");
            case "MISSING_ACTION" ->
                requester.contentType("application/json").body("{}").patch(PATH + "/{id}", orderId);
            case "INVALID_ACTION" -> patch(requester, orderId, "READY");
            default -> patch(requester, orderId, "TAKE");
        };
        result.then().statusCode(expected);
        if (expected >= 400)
        {
            result.then().contentType("application/problem+json").body("status", is(expected));
        }
    }

    private long place(long restaurantId)
    {
        long itemId = item(menu(restaurantId, "Lunch", true), "Bun", true);
        return orders
                .place(customer(),
                        new PlaceOrderDTO(List.of(new PlaceOrderDTO.OrderLine(itemId, 1)),
                                new PlaceOrderDTO.DeliveryAddress("Test Street 10", "1165", "Copenhagen"), "tok_visa"))
                .id();
    }

    private long agent(String name)
    {
        return user(name + "@example.test", "DELIVERY_AGENT");
    }

    private RequestSpecification request(long userId)
    {
        return given().port(port).auth().oauth2(tokens.issue(userId, 0).value());
    }

    private Response patch(RequestSpecification requester, long orderId, String action)
    {
        return requester.contentType("application/json").body("{\"action\":\"" + action + "\"}").patch(PATH + "/{id}",
                orderId);
    }

    private int takeWhenStarted(long agent, long orderId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException
    {
        RequestSpecification requester = request(agent);
        ready.countDown();
        start.await();
        return patch(requester, orderId, "TAKE").statusCode();
    }

    @Nested
    @TestPropertySource(properties = "FEATURE_ORDERING_ENABLED=false")
    class WhenOrderingIsDisabled
    {
        @LocalServerPort
        int disabledPort;

        @Test
        @DisplayName("Delivery endpoints - are hidden when ordering is disabled")
        void endpointsAreNotFound()
        {
            given().port(disabledPort).get(PATH).then().statusCode(404);
            given().port(disabledPort).patch(PATH + "/1").then().statusCode(404);
        }
    }
}
