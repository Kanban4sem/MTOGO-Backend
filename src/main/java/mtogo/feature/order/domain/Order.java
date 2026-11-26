package mtogo.feature.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mtogo.exceptions.ConflictException;
import mtogo.feature.commission.domain.CommissionAmounts;
import mtogo.feature.address.domain.Address;
import mtogo.feature.restaurant.domain.Restaurant;
import mtogo.feature.user.domain.User;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class Order
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Setter
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;
    @Setter
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_agent_id")
    private User deliveryAgent;
    @Setter
    @OneToOne(optional = false, fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "delivery_address_id", nullable = false, unique = true)
    private Address deliveryAddress;
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderChannel channel;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;
    // Set when the order is delivered, from the commission scale that applied then.
    @Column(precision = 10, scale = 2)
    private BigDecimal commission;
    @Column(name = "service_fee", precision = 10, scale = 2)
    private BigDecimal serviceFee;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderLine> lines = new ArrayList<>();
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderStatusChange> statusChanges = new ArrayList<>();

    public void addLine(OrderLine line)
    {
        line.setOrder(this);
        lines.add(line);
        total = total.add(line.getSubtotal());
    }

    public void assignDeliveryAgent(User agent)
    {
        if (status != OrderStatus.READY || deliveryAgent != null)
        {
            throw new ConflictException("The order is not available for delivery");
        }
        deliveryAgent = agent;
    }

    public boolean isAssignedTo(Long agentId)
    {
        return deliveryAgent != null && agentId.equals(deliveryAgent.getId());
    }

    /**
     * Moves the order along the lifecycle from the MVP and records the time the new
     * status was set. A new order can only move to Placed.
     *
     * @throws ConflictException
     *             when the lifecycle does not allow the move
     */
    public void moveTo(OrderStatus next, Instant at)
    {
        if (status == null)
        {
            requireMove(next == OrderStatus.PLACED, "no status", next);
        } else
        {
            requireMove(status.canMoveTo(next), status.name(), next);
        }
        status = next;
        // PostgreSQL keeps microseconds, so the time returned now matches the time
        // read back later.
        statusChanges.add(new OrderStatusChange(this, next, at.truncatedTo(ChronoUnit.MICROS)));
    }

    /**
     * Stores what the restaurant pays for this order. Only a delivered order has
     * these amounts, and they never change once stored, whatever later happens to
     * the commission scale.
     *
     * @throws IllegalStateException
     *             when the order isn't delivered or already has the amounts
     */
    public void recordCommission(CommissionAmounts amounts)
    {
        if (status != OrderStatus.DELIVERED || commission != null)
        {
            throw new IllegalStateException("Only a delivered order without amounts can get a commission");
        }
        commission = amounts.commission();
        serviceFee = amounts.serviceFee();
    }

    private static void requireMove(boolean isAllowed, String from, OrderStatus next)
    {
        if (!isAllowed)
        {
            throw new ConflictException("An order cannot move from " + from + " to " + next);
        }
    }

    public Instant getPlacedAt()
    {
        return getChangedAt(OrderStatus.PLACED);
    }

    /**
     * When the order first reached the given status, or {@code null} when it never
     * did.
     */
    public Instant getChangedAt(OrderStatus status)
    {
        return statusChanges.stream().filter(change -> change.getStatus() == status).findFirst()
                .map(OrderStatusChange::getChangedAt).orElse(null);
    }
}
