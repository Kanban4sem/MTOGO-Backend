package mtogo.feature.user.domain;

import java.util.List;
import mtogo.config.AppProperties;
import mtogo.feature.restaurant.data.RestaurantRepository;
import mtogo.feature.restaurant.domain.Restaurant;
import mtogo.feature.role.data.RoleRepository;
import mtogo.feature.role.domain.Role;
import mtogo.feature.role.domain.RoleName;
import mtogo.feature.user.data.UserRepository;
import mtogo.feature.user.presentation.dto.request.RegisterUserDTO;
import mtogo.security.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the seeded users of every role whose seed password is set (ADR-006).
 * It only creates missing users and never changes an existing one.
 */
@Service
public class UserSeedService
{
    private static final Logger LOG = LoggerFactory.getLogger(UserSeedService.class);
    private static final List<SeededUser> SEEDED_USERS = List.of(
            SeededUser.customer("Test", "Customer", "customer@mtogo.example", "+4500000001", "2800", "Kongens Lyngby"),
            SeededUser.restaurant("MTOGO", "Kebab", "kebab@mtogo.example", "+4500000002", "MTOGO Kebab"),
            SeededUser.restaurant("MTOGO", "Bakery", "bakery@mtogo.example", "+4500000003", "MTOGO Bakery"),
            SeededUser.deliveryAgent("Delivery", "Agent", "agent@mtogo.example", "+4500000004"));

    private final UserRepository users;
    private final RoleRepository roles;
    private final RestaurantRepository restaurants;
    private final UserMapper mapper;
    private final PasswordService passwords;

    public UserSeedService(UserRepository users, RoleRepository roles, RestaurantRepository restaurants,
            UserMapper mapper, PasswordService passwords)
    {
        this.users = users;
        this.roles = roles;
        this.restaurants = restaurants;
        this.mapper = mapper;
        this.passwords = passwords;
    }

    /**
     * Creates the missing seeded users of each role whose password is set. A blank
     * password skips that role. A password that breaks the password rules throws a
     * {@code BadRequestException} and rolls back the seeding of every role.
     */
    @Transactional
    public void seed(AppProperties.Seed seedPasswords)
    {
        for (RoleName roleName : RoleName.values())
        {
            seedRole(roleName, seedPasswords.passwordFor(roleName));
        }
    }

    private void seedRole(RoleName roleName, String password)
    {
        if (!StringUtils.hasText(password))
        {
            LOG.info("Skipped the seeded {} users, because their seed password is not set", roleName);
            return;
        }
        // Hashing before the existence checks applies the password rules even when
        // every seeded user of the role already exists.
        String passwordHash = passwords.hash(password);
        Role role = roles.findByName(roleName).orElseThrow();
        for (SeededUser seededUser : SEEDED_USERS)
        {
            if (seededUser.role() != roleName)
            {
                continue;
            }
            if (users.existsByEmail(seededUser.registration().email()))
            {
                LOG.warn("Skipped the seeded user {}, because a user with that email already exists",
                        seededUser.registration().email());
                continue;
            }
            users.save(toUser(seededUser, role, passwordHash));
        }
    }

    private User toUser(SeededUser seededUser, Role role, String passwordHash)
    {
        User user = mapper.toEntity(seededUser.registration());
        user.activate(passwordHash, role, restaurantOf(seededUser));
        return user;
    }

    private Restaurant restaurantOf(SeededUser seededUser)
    {
        if (seededUser.restaurantName() == null)
        {
            return null;
        }
        List<Restaurant> matches = restaurants.findAllByName(seededUser.restaurantName());
        if (matches.size() != 1)
        {
            throw new IllegalStateException("Seeded user %s needs exactly one restaurant named %s, but found %d"
                    .formatted(seededUser.registration().email(), seededUser.restaurantName(), matches.size()));
        }
        return matches.getFirst();
    }

    /**
     * The registration holds the profile only. Its password fields stay empty,
     * because seeded users get their role's seed password.
     */
    private record SeededUser(RoleName role, RegisterUserDTO registration, String restaurantName)
    {
        static SeededUser customer(String firstName, String lastName, String email, String phoneNumber,
                String postalCode, String city)
        {
            return new SeededUser(RoleName.CUSTOMER, new RegisterUserDTO(firstName, lastName, email, null, null,
                    phoneNumber, null, postalCode, city, "Denmark"), null);
        }

        static SeededUser restaurant(String firstName, String lastName, String email, String phoneNumber,
                String restaurantName)
        {
            return new SeededUser(RoleName.RESTAURANT, profileOnly(firstName, lastName, email, phoneNumber),
                    restaurantName);
        }

        static SeededUser deliveryAgent(String firstName, String lastName, String email, String phoneNumber)
        {
            return new SeededUser(RoleName.DELIVERY_AGENT, profileOnly(firstName, lastName, email, phoneNumber), null);
        }

        private static RegisterUserDTO profileOnly(String firstName, String lastName, String email, String phoneNumber)
        {
            return new RegisterUserDTO(firstName, lastName, email, null, null, phoneNumber, null, null, null, null);
        }
    }
}
