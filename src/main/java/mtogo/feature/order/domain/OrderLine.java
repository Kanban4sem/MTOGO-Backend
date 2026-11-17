package mtogo.feature.order.domain;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mtogo.feature.restaurant.domain.MenuItem;

@Entity
@Table(name = "order_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Set by Order.addLine, which keeps the order total in step with its lines.
    @Setter(AccessLevel.PACKAGE)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private int quantity;

    /**
     * Copies the menu item's name and unit price as they are when the order is
     * placed, so a later change to the menu does not change the order.
     */
    public static OrderLine of(MenuItem menuItem, int quantity)
    {
        OrderLine line = new OrderLine();
        line.menuItem = menuItem;
        line.name = menuItem.getName();
        line.unitPrice = menuItem.getPrice();
        line.quantity = quantity;
        return line;
    }

    public BigDecimal getSubtotal()
    {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
