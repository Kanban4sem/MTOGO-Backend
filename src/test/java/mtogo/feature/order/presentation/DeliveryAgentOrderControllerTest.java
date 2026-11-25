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
import mtogo.feature.order.domain.OrderStatus;
import mtogo.feature.order.domain.OrderService;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateRestaurantOrderStatusDTO;
import mtogo.security.JwtService;
import mtogo.testsupport.AuthenticatedEndpointTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

@AuthenticatedEndpointTest
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
    @DisplayName("Delivery - one agent takes, picks up and delivers a ready order and sees its status names, lines and total")
    void deliverReadyOrder()
    {
        long restaurantId = restaurant("Bakery", true);
        long restaurantUser = restaurantUser("restaurant@example.test", restaurantId);
        long agent = deliveryAgent("first");
        long orderId = place(restaurantId);
        orders.updateRestaurantOrderStatus(restaurantUser, orderId,
                new UpdateRestaurantOrderStatusDTO(OrderStatus.ACCEPTED));
        orders.updateRestaurantOrderStatus(restaurantUser, orderId,
                new UpdateRestaurantOrderStatusDTO(OrderStatus.READY));

        request(agent).get(PATH).then().statusCode(200).body("id", contains((int) orderId))
                .body("[0].status", is("READY")).body("[0].statusName.danish", is("Klar til afhentning"))
                .body("[0].statusName.english", is("Ready for pickup")).body("[0].assignedToMe", is(false))
                .body("[0]", not(hasKey("isAssignedToMe"))).body("[0]", not(hasKey("deliveryCity")))
                .body("[0].restaurantId", is((int) restaurantId)).body("[0].restaurantName", is("Bakery"))
                .body("[0].total", is(25.00f)).body("[0].lines.size()", is(1)).body("[0].lines[0].name", is("Bun"))
                .body("[0].lines[0].unitPrice", is(12.50f)).body("[0].lines[0].quantity", is(2))
                .body("[0].lines[0].subtotal", is(25.00f)).body("[0].deliveryAddress.street", is("Test Street 10"))
                .body("[0].deliveryAddress.postalCode", is("1165")).body("[0].deliveryAddress.city", is("Copenhagen"))
                .body("[0].deliveryAddress.country", is("Denmark"));
        patch(request(agent), orderId, "TAKE").then().statusCode(200).body("id", is((int) orderId))
                .body("status", is("READY")).body("statusName.danish", is("Klar til afhentning"))
                .body("statusName.english", is("Ready for pickup")).body("assignedToMe", is(true))
                .body("$", not(hasKey("isAssignedToMe"))).body("$", not(hasKey("deliveryCity")))
                .body("deliveryAddress.street", is("Test Street 10")).body("lines[0].name", is("Bun"))
                .body("total", is(25.00f));
        patch(request(agent), orderId, "PICK_UP").then().statusCode(200).body("status", is("PICKED_UP"))
                .body("statusName.danish", is("Afhentet")).body("statusName.english", is("Picked up"));
        patch(request(agent), orderId, "DELIVER").then().statusCode(200).body("status", is("DELIVERED"))
                .body("statusName.danish", is("Leveret")).body("statusName.english", is("Delivered"))
                .body("assignedToMe", is(true));

        assertEquals(agent, readDeliveryAgent(orderId));
        assertEquals(List.of("PLACED", "ACCEPTED", "READY", "PICKED_UP", "DELIVERED"), orderHistory(orderId));
    }

    @Test
    @DisplayName("Take - simultaneous requests can assign an order to only one agent")
    void takeConcurrently() throws Exception
    {
        long orderId = place(restaurant("Bakery", true));
        jdbc.update("UPDATE orders SET status = 'READY' WHERE id = ?", orderId);
        long firstAgent = deliveryAgent("first");
        long secondAgent = deliveryAgent("second");
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

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/delivery-agent-order-controller/get-all-access.csv", numLinesToSkip = 1)
    @DisplayName("Get all - only delivery agents can list deliveries")
    void getAllAccess(String role, int expected, String reason)
    {
        Response result = requester(role).get(PATH);

        assertStatus(result, expected);
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/delivery-agent-order-controller/update-access.csv", numLinesToSkip = 1)
    @DisplayName("Update - only delivery agents can change a delivery, and only one that exists")
    void updateAccess(String role, String order, int expected, String reason)
    {
        long orderId = place(restaurant("Bakery", true));
        String requestedId = switch (order)
        {
            case "EXISTING" -> Long.toString(orderId);
            case "UNKNOWN" -> Long.toString(orderId + 1);
            default -> order;
        };

        Response result = patch(requester(role), requestedId, "TAKE");

        assertStatus(result, expected);
        assertEquals("PLACED", readStatus(orderId));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/delivery-agent-order-controller/update-fields.csv", numLinesToSkip = 1)
    @DisplayName("Update - accepts only a supported action")
    void updateFields(String body, int expected, String reason)
    {
        long orderId = place(restaurant("Bakery", true));

        Response result = patchBody(requester("DELIVERY_AGENT"), orderId, body);

        assertStatus(result, expected);
        assertEquals("PLACED", readStatus(orderId));
    }

    private RequestSpecification requester(String role)
    {
        return switch (role)
        {
            case "ANONYMOUS" -> given().port(port);
            default -> request(user("requester@example.test", role));
        };
    }

    private static void assertStatus(Response result, int expected)
    {
        result.then().statusCode(expected);
        if (expected >= 400)
        {
            result.then().contentType("application/problem+json").body("status", is(expected));
        }
    }

    private long place(long restaurantId)
    {
        long itemId = item(menu(restaurantId, "Lunch", true), "Bun", "12.50");
        return orders
                .place(customer(),
                        new PlaceOrderDTO(List.of(new PlaceOrderDTO.OrderLine(itemId, 2)),
                                new PlaceOrderDTO.DeliveryAddress("Test Street 10", "1165", "Copenhagen"), "tok_visa"))
                .id();
    }

    private RequestSpecification request(long userId)
    {
        return given().port(port).auth().oauth2(tokens.issue(userId, 0).value());
    }

    private Response patch(RequestSpecification requester, Object orderId, String action)
    {
        return patchBody(requester, orderId, "{\"action\":\"" + action + "\"}");
    }

    private Response patchBody(RequestSpecification requester, Object orderId, String body)
    {
        return requester.contentType("application/json").body(body).patch(PATH + "/{id}", orderId);
    }

    private int takeWhenStarted(long agent, long orderId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException
    {
        RequestSpecification requester = request(agent);
        ready.countDown();
        start.await();
        return patch(requester, orderId, "TAKE").statusCode();
    }
}
