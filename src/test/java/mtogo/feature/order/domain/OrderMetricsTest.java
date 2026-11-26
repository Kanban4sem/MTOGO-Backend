package mtogo.feature.order.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import mtogo.exceptions.ApiException;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.presentation.dto.request.PlaceOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateDeliveryAgentOrderDTO;
import mtogo.feature.order.presentation.dto.request.UpdateRestaurantOrderStatusDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

// The registry outlives a single test, so every meter is asserted as the change
// a test causes. No test transaction, so the after-commit listener runs.
@SpringBootTest
class OrderMetricsTest extends OrderTestData
{
    @Autowired
    OrderService orders;
    @Autowired
    MeterRegistry registry;
    @Autowired
    TransactionTemplate transaction;

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/order-metrics/place-counted.csv", numLinesToSkip = 1)
    @DisplayName("Place - counts an order only when it is placed with a confirmed payment")
    void placeCountsPlacedOrders(String request, int expected, String reason)
    {
        long customerId = customer();
        long menuId = menu(restaurant("Requested", true), "Lunch", true);
        long itemId = item(menuId, "Meal", true);
        List<PlaceOrderDTO.OrderLine> lines = List.of(new PlaceOrderDTO.OrderLine(itemId, 1));
        if (request.equals("twoRestaurants"))
        {
            long otherMenuId = menu(restaurant("Other", true), "Other lunch", true);
            lines = List.of(new PlaceOrderDTO.OrderLine(itemId, 1),
                    new PlaceOrderDTO.OrderLine(item(otherMenuId, "Other meal", true), 1));
        }
        PlaceOrderDTO order = placeOrder(lines, request.equals("declined") ? "decline" : "tok_visa");
        double before = placedOrders();

        if (request.equals("confirmed"))
        {
            orders.place(customerId, order);
        } else
        {
            assertThrows(ApiException.class, () -> orders.place(customerId, order));
        }

        assertThat(placedOrders() - before, is((double) expected));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/order-metrics/update-restaurant-order-status-rejected.csv", numLinesToSkip = 1)
    @DisplayName("Update restaurant order status - counts an order only when the restaurant rejects it")
    void updateRestaurantOrderStatusCountsRejectedOrders(OrderStatus status, int expected, String reason)
    {
        long restaurantId = restaurant("Requested", true);
        long requesterId = restaurantUser("restaurant@example.test", restaurantId);
        long orderId = order(customer(), restaurantId);
        double before = rejectedOrders();

        orders.updateRestaurantOrderStatus(requesterId, orderId, new UpdateRestaurantOrderStatusDTO(status));

        assertThat(rejectedOrders() - before, is((double) expected));
    }

    @Test
    @DisplayName("Update restaurant order status - a rejection that rolls back is not counted")
    void updateRestaurantOrderStatusDoesNotCountRolledBackRejection()
    {
        long restaurantId = restaurant("Requested", true);
        long requesterId = restaurantUser("restaurant@example.test", restaurantId);
        long orderId = order(customer(), restaurantId);
        double before = rejectedOrders();

        transaction.executeWithoutResult(status -> {
            orders.updateRestaurantOrderStatus(requesterId, orderId,
                    new UpdateRestaurantOrderStatusDTO(OrderStatus.REJECTED));
            status.setRollbackOnly();
        });

        assertThat(rejectedOrders() - before, is(0.0));
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId),
                is(OrderStatus.PLACED.name()));
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/order-metrics/unassigned-orders-count.csv", numLinesToSkip = 1)
    @DisplayName("Unassigned orders - counts only ready orders no delivery agent has taken")
    void unassignedOrdersCountsReadyOrdersWithoutDeliveryAgent(OrderStatus status, boolean hasDeliveryAgent,
            int expected, String reason)
    {
        Long agentId = hasDeliveryAgent ? deliveryAgent("agent") : null;
        double before = unassignedOrders();

        setOrder(order(customer(), restaurant("Requested", true)), status.name(), agentId);

        assertThat(unassignedOrders() - before, is((double) expected));
    }

    @Test
    @DisplayName("Unassigned orders - an order stops counting when a delivery agent takes it")
    void unassignedOrdersDropsAnOrderADeliveryAgentTakes()
    {
        long agentId = deliveryAgent("agent");
        long orderId = order(customer(), restaurant("Requested", true));
        setOrder(orderId, OrderStatus.READY.name(), null);
        double before = unassignedOrders();

        orders.updateDeliveryAgentOrder(agentId, orderId, new UpdateDeliveryAgentOrderDTO(DeliveryAction.TAKE));

        assertThat(unassignedOrders() - before, is(-1.0));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/order/order-metrics/update-delivery-agent-order-delivery-time.csv", numLinesToSkip = 1)
    @DisplayName("Update delivery agent order - records the time from Placed to Delivered only when the order is delivered")
    void updateDeliveryAgentOrderRecordsDeliveryTime(DeliveryAction action, long expected, String reason)
    {
        long agentId = deliveryAgent("agent");
        long orderId = order(customer(), restaurant("Requested", true));
        OrderStatus status = action == DeliveryAction.DELIVER ? OrderStatus.PICKED_UP : OrderStatus.READY;
        setOrder(orderId, status.name(), agentId);
        long countBefore = deliveryTime().count();
        double microsBefore = deliveryTime().totalTime(TimeUnit.MICROSECONDS);

        orders.updateDeliveryAgentOrder(agentId, orderId, new UpdateDeliveryAgentOrderDTO(action));

        long expectedMicros = expected == 0
                ? 0
                : Duration.between(changedAt(orderId, OrderStatus.PLACED), changedAt(orderId, OrderStatus.DELIVERED))
                        .toNanos() / 1000;
        assertThat(deliveryTime().count() - countBefore, is(expected));
        assertThat(Math.round(deliveryTime().totalTime(TimeUnit.MICROSECONDS) - microsBefore), is(expectedMicros));
    }

    @Test
    @DisplayName("Update delivery agent order - delivers an order without a Placed time and records no delivery time")
    void updateDeliveryAgentOrderSkipsDeliveryTimeWithoutPlacedTime()
    {
        long agentId = deliveryAgent("agent");
        long orderId = order(customer(), restaurant("Requested", true));
        setOrder(orderId, OrderStatus.PICKED_UP.name(), agentId);
        jdbc.update("DELETE FROM order_status_changes WHERE order_id = ? AND status = 'PLACED'", orderId);
        long countBefore = deliveryTime().count();

        orders.updateDeliveryAgentOrder(agentId, orderId, new UpdateDeliveryAgentOrderDTO(DeliveryAction.DELIVER));

        assertThat(deliveryTime().count() - countBefore, is(0L));
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId),
                is(OrderStatus.DELIVERED.name()));
    }

    private double placedOrders()
    {
        return registry.get("mtogo.orders.placed").counter().count();
    }

    private double rejectedOrders()
    {
        return registry.get("mtogo.orders.rejected").counter().count();
    }

    private double unassignedOrders()
    {
        return registry.get("mtogo.orders.unassigned").gauge().value();
    }

    private Timer deliveryTime()
    {
        return registry.get("mtogo.order.delivery.time").timer();
    }

    private Instant changedAt(long orderId, OrderStatus status)
    {
        return jdbc.queryForObject("SELECT changed_at FROM order_status_changes WHERE order_id = ? AND status = ?",
                Timestamp.class, orderId, status.name()).toInstant().truncatedTo(ChronoUnit.MICROS);
    }

    private long order(long customerId, long restaurantId)
    {
        long itemId = item(menu(restaurantId, "Lunch", true), "Meal", true);
        return orders.place(customerId, placeOrder(List.of(new PlaceOrderDTO.OrderLine(itemId, 1)), "tok_visa")).id();
    }

    private static PlaceOrderDTO placeOrder(List<PlaceOrderDTO.OrderLine> lines, String paymentToken)
    {
        return new PlaceOrderDTO(lines, new PlaceOrderDTO.DeliveryAddress("Nørregade 10", "1165", "København K"),
                paymentToken);
    }
}
