package mtogo.feature.order.domain;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_status_changes")
@Getter
@NoArgsConstructor
public class OrderStatusChange
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    OrderStatusChange(Order order, OrderStatus status, Instant changedAt)
    {
        this.order = order;
        this.status = status;
        this.changedAt = changedAt;
    }
}
