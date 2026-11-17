package mtogo.feature.order.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import mtogo.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class OrderTest
{
    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/order/move-to-transitions.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Move to - follows the order status lifecycle and records each move")
    void moveToFollowsLifecycle(OrderStatus current, OrderStatus requested, String expected, String reason)
    {
        Order order = orderWithStatus(current);
        int changesBefore = order.getStatusChanges().size();

        if (expected.equals("ACCEPT"))
        {
            Instant before = Instant.now().minusSeconds(1);
            order.moveTo(requested);
            assertThat(order.getStatus(), is(requested));
            assertThat(order.getStatusChanges(), hasSize(changesBefore + 1));
            OrderStatusChange change = order.getStatusChanges().getLast();
            assertThat(change.getStatus(), is(requested));
            assertThat(change.getOrder(), is(sameInstance(order)));
            assertThat(change.getChangedAt(),
                    is(both(greaterThan(before)).and(lessThan(Instant.now().plusSeconds(1)))));
        } else
        {
            ApiException exception = assertThrows(ApiException.class, () -> order.moveTo(requested));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(order.getStatus(), is(current));
            assertThat(order.getStatusChanges(), hasSize(changesBefore));
        }
    }

    // Walks the lifecycle to reach the status, so the order holds the same
    // history it would have in production.
    private static Order orderWithStatus(OrderStatus status)
    {
        Order order = new Order();
        List<OrderStatus> path = switch (status == null ? "NONE" : status.name())
        {
            case "NONE" -> List.of();
            case "PLACED" -> List.of(OrderStatus.PLACED);
            case "REJECTED" -> List.of(OrderStatus.PLACED, OrderStatus.REJECTED);
            case "ACCEPTED" -> List.of(OrderStatus.PLACED, OrderStatus.ACCEPTED);
            case "READY" -> List.of(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.READY);
            case "PICKED_UP" ->
                List.of(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.READY, OrderStatus.PICKED_UP);
            default -> List.of(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.READY, OrderStatus.PICKED_UP,
                    OrderStatus.DELIVERED);
        };
        path.forEach(order::moveTo);
        return order;
    }
}
