package mtogo.feature.user.domain;

import java.util.List;
import mtogo.config.AppProperties;
import mtogo.feature.address.domain.AddressMapper;
import mtogo.feature.restaurant.data.RestaurantRepository;
import mtogo.feature.restaurant.domain.Restaurant;
import mtogo.feature.role.data.RoleRepository;
import mtogo.feature.role.domain.Role;
import mtogo.feature.role.domain.RoleName;
import mtogo.feature.user.data.UserRepository;
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
            new SeededUser(RoleName.CUSTOMER, "customer@mtogo.example", "Test", "Customer", "+4500000001", null, true),
            new SeededUser(RoleName.RESTAURANT, "kebab@mtogo.example", "MTOGO", "Kebab", "+4500000002", "MTOGO Kebab",
                    false),
            new SeededUser(RoleName.RESTAURANT, "bakery@mtogo.example", "MTOGO", "Bakery", "+4500000003",
                    "MTOGO Bakery", false),
            new SeededUser(RoleName.DELIVERY_AGENT, "agent@mtogo.example", "Delivery", "Agent", "+4500000004", null,
                    false));

    private final UserRepository users;
    private final RoleRepository roles;
    private final RestaurantRepository restaurants;
    private final AddressMapper addressMapper;
    private final PasswordService passwords;

    public UserSeedService(UserRepository users, RoleRepository roles, RestaurantRepository restaurants,
            AddressMapper addressMapper, PasswordService passwords)
    {
        this.users = users;
        this.roles = roles;
        this.restaurants = restaurants;
        this.addressMapper = addressMapper;
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
            if (users.existsByEmail(seededUser.email()))
            {
                LOG.warn("Skipped the seeded user {}, because a user with that email already exists",
                        seededUser.email());
                continue;
            }
            users.save(toUser(seededUser, role, passwordHash));
        }
    }

    private User toUser(SeededUser seededUser, Role role, String passwordHash)
    {
        UserProfile profile = new UserProfile();
        profile.setFirstName(seededUser.firstName());
        profile.setLastName(seededUser.lastName());
        profile.setPhoneNumber(seededUser.phoneNumber());
        profile.setPreferredLanguage("da");
        if (seededUser.hasAddress())
        {
            profile.setAddress(addressMapper.toEntity("2800", "Kongens Lyngby", "Denmark"));
        }
        User user = new User();
        user.setEmail(seededUser.email());
        user.setProfile(profile);
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
                    .formatted(seededUser.email(), seededUser.restaurantName(), matches.size()));
        }
        return matches.getFirst();
    }

    private record SeededUser(RoleName role, String email, String firstName, String lastName, String phoneNumber,
            String restaurantName, boolean hasAddress)
    {
    }
}
