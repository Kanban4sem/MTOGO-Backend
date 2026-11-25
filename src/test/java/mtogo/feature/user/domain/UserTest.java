package mtogo.feature.user.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mtogo.exceptions.ApiException;
import mtogo.feature.restaurant.domain.Restaurant;
import mtogo.feature.role.domain.Role;
import mtogo.feature.role.domain.RoleName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class UserTest
{
    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/user/user/assign-role-restaurant.csv", numLinesToSkip = 1)
    @DisplayName("Assign role - only a restaurant user has a restaurant, and always has one")
    void assignRoleLinksOnlyRestaurantUsersToARestaurant(RoleName roleName, boolean hasRestaurant, String expected,
            String reason)
    {
        User user = new User();
        Role role = new Role();
        role.setName(roleName);
        Restaurant restaurant = hasRestaurant ? new Restaurant() : null;

        if (expected.equals("ACCEPT"))
        {
            user.assignRole(role, restaurant);
            assertThat(user.getRole(), is(sameInstance(role)));
            assertThat(user.getRestaurant(), is(restaurant));
        } else
        {
            ApiException exception = assertThrows(ApiException.class, () -> user.assignRole(role, restaurant));
            assertThat(exception.getClass().getSimpleName(), is(expected));
            assertThat(user.getRole(), is(nullValue()));
            assertThat(user.getRestaurant(), is(nullValue()));
        }
    }
}
