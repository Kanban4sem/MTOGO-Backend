package mtogo.feature.restaurant.domain;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
public class MenuItem
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;
    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    public Restaurant getRestaurant()
    {
        return menu.getRestaurant();
    }

    public boolean isOnActiveMenu()
    {
        return menu.isActive();
    }
}
