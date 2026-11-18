package mtogo.feature.restaurant;

import mtogo.testsupport.SharedDatabase;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SharedDatabase
public abstract class RestaurantTestData
{
    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanRestaurants()
    {
        jdbc.update("DELETE FROM menu_items");
        jdbc.update("DELETE FROM menus");
        jdbc.update("DELETE FROM restaurants");
    }

    protected long restaurant(String name, boolean acceptsOrders)
    {
        return jdbc.queryForObject("INSERT INTO restaurants (name, accepts_orders) VALUES (?, ?) RETURNING id",
                Long.class, name, acceptsOrders);
    }

    protected long menu(long restaurantId, String name, boolean active)
    {
        return jdbc.queryForObject(
                "INSERT INTO menus (restaurant_id, name, description, is_active) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, restaurantId, name, "Menu description", active);
    }

    protected long item(long menuId, String name, boolean available)
    {
        return jdbc.queryForObject(
                "INSERT INTO menu_items (menu_id, name, description, price, image_url, is_available) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, menuId, name, "Item description", new BigDecimal("89.95"), "/images/meal.jpg", available);
    }
}
