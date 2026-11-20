package mtogo.feature.user.domain;

import mtogo.exceptions.BadRequestException;
import mtogo.feature.restaurant.domain.Restaurant;
import mtogo.feature.role.domain.Role;
import mtogo.feature.role.domain.RoleName;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(name = "is_active", nullable = false)
    private boolean isActive;
    @Column(nullable = false)
    private long sessionVersion;
    @Setter(AccessLevel.NONE)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;
    @OneToOne(optional = false, fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "profile_id", nullable = false, unique = true)
    private UserProfile profile;

    // Registration and seeding create a user the same way.
    public void activate(String passwordHash, Role role, Restaurant restaurant)
    {
        assignRole(role, restaurant);
        this.passwordHash = passwordHash;
        this.isActive = true;
    }

    // The database cannot tie the restaurant to the role, because the role is a row
    // in another table, so every user gets both here.
    public void assignRole(Role role, Restaurant restaurant)
    {
        boolean isRestaurantUser = role.getName() == RoleName.RESTAURANT;
        if (isRestaurantUser != (restaurant != null))
        {
            throw new BadRequestException(isRestaurantUser
                    ? "A restaurant user must have a restaurant"
                    : "Only a restaurant user can have a restaurant");
        }
        this.role = role;
        this.restaurant = restaurant;
    }
}
