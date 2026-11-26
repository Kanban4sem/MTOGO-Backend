package mtogo.feature.order.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import mtogo.exceptions.ApiException;
import mtogo.feature.commission.domain.CommissionAmounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class OrderTest
{
    private static final Instant AT = Instant.parse("2026-11-25T12:00:00Z");
    private static final CommissionAmounts FIRST = new CommissionAmounts(new BigDecimal("6.00"),
            new BigDecimal("5.00"));
    private static final CommissionAmounts SECOND = new CommissionAmounts(new BigDecimal("4.80"),
            new BigDecimal("2.50"));

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/order/move-to-transitions.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Move to - follows the order status lifecycle and records each move")
    void moveToFollowsLifecycle(OrderStatus current, OrderStatus requested, String expected, String reason)
    {
        Order order = orderWithStatus(current);
        int changesBefore = order.getStatusChanges().size();

        if (expected.equals("ACCEPT"))
        {
            order.moveTo(requested, AT);
            assertThat(order.getStatus(), is(requested));
            assertThat(order.getStatusChanges(), hasSize(changesBefore + 1));
            OrderStatusChange change = order.getStatusChanges().getLast();
            assertThat(change.getStatus(), is(requested));
            assertThat(change.getOrder(), is(sameInstance(order)));
            assertThat(change.getChangedAt(), is(AT));
        } else
        {
            ApiException exception = assertThrows(ApiException.class, () -> order.moveTo(requested, AT));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(order.getStatus(), is(current));
            assertThat(order.getStatusChanges(), hasSize(changesBefore));
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/order/order/record-commission.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Record commission - only a delivered order without amounts gets them, and stored amounts never change")
    void recordCommissionOnlyOnceWhenDelivered(OrderStatus status, boolean hasAmounts, String expected, String reason)
    {
        Order order = orderWithStatus(status);
        if (hasAmounts)
        {
            order.recordCommission(FIRST);
        }
        BigDecimal commissionBefore = order.getCommission();
        BigDecimal serviceFeeBefore = order.getServiceFee();

        if (expected.equals("ACCEPT"))
        {
            order.recordCommission(SECOND);
            assertThat(order.getCommission(), is(SECOND.commission()));
            assertThat(order.getServiceFee(), is(SECOND.serviceFee()));
        } else
        {
            RuntimeException exception = assertThrows(RuntimeException.class, () -> order.recordCommission(SECOND));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(order.getCommission(), is(commissionBefore));
            assertThat(order.getServiceFee(), is(serviceFeeBefore));
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
        path.forEach(next -> order.moveTo(next, AT));
        return order;
    }
}
