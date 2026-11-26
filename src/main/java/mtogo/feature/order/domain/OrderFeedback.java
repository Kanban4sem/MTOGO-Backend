package mtogo.feature.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_feedback")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderFeedback
{
    @Id
    @Column(name = "order_id")
    private Long orderId;
    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(length = 100)
    private String title;
    @Column(length = 1000)
    private String description;
    @Column(name = "food_rating", nullable = false)
    private int foodRating;
    @Column(name = "overall_rating", nullable = false)
    private int overallRating;
    @Column(name = "delivery_rating", nullable = false)
    private int deliveryRating;

    OrderFeedback(Order order, String title, String description, int foodRating, int overallRating, int deliveryRating)
    {
        this.order = order;
        this.title = title;
        this.description = description;
        this.foodRating = foodRating;
        this.overallRating = overallRating;
        this.deliveryRating = deliveryRating;
    }
}
