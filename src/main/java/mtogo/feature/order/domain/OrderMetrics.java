package mtogo.feature.order.domain;

import java.time.Duration;
import java.time.Instant;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import mtogo.feature.order.data.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The order feature's metrics for Prometheus (ADR-007). Status changes are
 * recorded after the transaction commits, so work that rolls back is never
 * counted. The number of unassigned orders is read from the database on every
 * scrape. A metric never makes a request fail.
 */
@Slf4j
@Component
public class OrderMetrics
{
    private final Counter placedOrders;
    private final Counter rejectedOrders;
    private final Timer deliveryTime;

    public OrderMetrics(MeterRegistry registry, OrderRepository orders)
    {
        placedOrders = Counter.builder("mtogo.orders.placed").description("Orders placed with a confirmed payment")
                .register(registry);
        rejectedOrders = Counter.builder("mtogo.orders.rejected").description("Orders a restaurant rejected")
                .register(registry);
        deliveryTime = Timer.builder("mtogo.order.delivery.time")
                .description("Time from an order reaching Placed to reaching Delivered")
                .serviceLevelObjectives(minutes(10), minutes(15), minutes(20), minutes(30), minutes(45), minutes(60),
                        minutes(90), minutes(120))
                .register(registry);
        Gauge.builder("mtogo.orders.unassigned", orders,
                repository -> repository.countByStatusAndDeliveryAgentIsNull(OrderStatus.READY))
                .description("Ready orders no delivery agent has taken").strongReference(true).register(registry);
    }

    @TransactionalEventListener
    public void record(OrderStatusChanged change)
    {
        if (change.status() == OrderStatus.PLACED)
        {
            placedOrders.increment();
        } else if (change.status() == OrderStatus.REJECTED)
        {
            rejectedOrders.increment();
        } else if (change.status() == OrderStatus.DELIVERED)
        {
            recordDeliveryTime(change);
        }
    }

    private void recordDeliveryTime(OrderStatusChanged change)
    {
        if (change.placedAt() == null)
        {
            log.warn("Delivery time not recorded: the delivered order has no Placed time in its history");
            return;
        }
        deliveryTime.record(Duration.between(change.placedAt(), change.deliveredAt()));
    }

    private static Duration minutes(long minutes)
    {
        return Duration.ofMinutes(minutes);
    }

    /**
     * An order moved to a new status. For Delivered, it carries the times from the
     * order history, so the delivery time matches the history exactly.
     */
    record OrderStatusChanged(OrderStatus status, Instant placedAt, Instant deliveredAt)
    {
        static OrderStatusChanged of(Order order)
        {
            return order.getStatus() == OrderStatus.DELIVERED
                    ? new OrderStatusChanged(order.getStatus(), order.getPlacedAt(),
                            order.getChangedAt(OrderStatus.DELIVERED))
                    : new OrderStatusChanged(order.getStatus(), null, null);
        }
    }
}
