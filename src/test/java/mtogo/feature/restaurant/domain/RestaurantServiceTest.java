package mtogo.feature.restaurant.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import mtogo.exceptions.NotFoundException;
import mtogo.feature.restaurant.RestaurantTestData;
import mtogo.feature.restaurant.presentation.dto.response.MenuDTO;
import mtogo.feature.restaurant.presentation.dto.response.MenuItemDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "FEATURE_AUTH_ENABLED=false")
class RestaurantServiceTest extends RestaurantTestData
{
    @Autowired
    RestaurantService restaurants;

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/restaurant/restaurant-service/get-all-acceptance.csv", numLinesToSkip = 1)
    @DisplayName("Get all - returns only restaurants accepting orders in ID order")
    void getAllAcceptance(boolean acceptsOrders, String expected, String reason)
    {
        long first = restaurant("First", acceptsOrders);
        long second = restaurant("Second", true);
        var result = restaurants.getAll();
        assertEquals(acceptsOrders ? java.util.List.of(first, second) : java.util.List.of(second),
                result.stream().map(r -> r.id()).toList());
        assertTrue(result.stream().allMatch(r -> r.acceptsOrders()));
        assertEquals("Second", result.getLast().name());
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/restaurant/restaurant-service/get-menus-restaurant.csv", numLinesToSkip = 1)
    @DisplayName("Get menus - requires an existing restaurant accepting orders")
    void getMenusRestaurant(boolean exists, boolean acceptsOrders, String expected, String reason)
    {
        long id = restaurant("Requested", acceptsOrders);
        if (!exists)
        {
            jdbc.update("DELETE FROM restaurants WHERE id = ?", id);
        }
        if (expected.equals("ACCEPT"))
        {
            assertTrue(restaurants.getMenus(id).isEmpty());
        } else
        {
            assertThrows(NotFoundException.class, () -> restaurants.getMenus(id));
        }
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/restaurant/restaurant-service/get-menus-visibility.csv", numLinesToSkip = 1)
    @DisplayName("Get menus - includes only active menus and their available items")
    void getMenusVisibility(boolean active, boolean available, String expected, String reason)
    {
        long id = restaurant("Requested", true);
        long menuId = menu(id, "Lunch", active);
        long itemId = item(menuId, "Meal", available);
        var result = restaurants.getMenus(id);
        assertEquals(active ? 1 : 0, result.size());
        if (active)
        {
            MenuDTO actual = result.getFirst();
            assertEquals(menuId, actual.id());
            assertEquals(available ? java.util.List.of(itemId) : java.util.List.of(),
                    actual.items().stream().map(MenuItemDTO::id).toList());
        }
    }

    @Test
    @DisplayName("Get menus - keeps restaurants and menus isolated and preserves the ordered response contract")
    void getMenusIsolationAndMapping()
    {
        long id = restaurant("Requested", true);
        long other = restaurant("Other", true);
        long first = menu(id, "Lunch", true);
        long second = menu(id, "Dinner", true);
        long otherMenu = menu(other, "Other menu", true);
        long firstItem = item(first, "First meal", true);
        long secondItem = item(first, "Second meal", true);
        long dinnerItem = item(second, "Dinner meal", true);
        item(otherMenu, "Other restaurant's meal", true);

        var result = restaurants.getMenus(id);
        assertEquals(java.util.List.of(first, second), result.stream().map(MenuDTO::id).toList());
        MenuDTO lunch = result.getFirst();
        assertEquals(id, lunch.restaurantId());
        assertEquals("Lunch", lunch.name());
        assertEquals("Menu description", lunch.description());
        assertEquals(java.util.List.of(firstItem, secondItem), lunch.items().stream().map(MenuItemDTO::id).toList());
        assertEquals(java.util.List.of(dinnerItem), result.getLast().items().stream().map(MenuItemDTO::id).toList());
        MenuItemDTO meal = lunch.items().getFirst();
        assertEquals("First meal", meal.name());
        assertEquals("Item description", meal.description());
        assertEquals(new BigDecimal("89.95"), meal.price());
        assertEquals("/images/meal.jpg", meal.imageUrl());
        assertTrue(meal.available());
    }

    @Test
    @DisplayName("Get all - a catalogue without restaurants is an empty list")
    void getAllEmpty()
    {
        assertTrue(restaurants.getAll().isEmpty());
    }
}
