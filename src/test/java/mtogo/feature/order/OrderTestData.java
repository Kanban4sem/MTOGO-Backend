package mtogo.feature.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mtogo.feature.restaurant.RestaurantTestData;
import org.junit.jupiter.api.AfterEach;

public abstract class OrderTestData extends RestaurantTestData
{
    // Runs after each test, because RestaurantTestData deletes menu items before
    // each test and order lines must be gone by then.
    @AfterEach
    void cleanOrders()
    {
        jdbc.update("DELETE FROM order_status_changes");
        jdbc.update("DELETE FROM order_lines");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM addresses");
        jdbc.update("DELETE FROM postal_codes");
    }

    protected long user(String email, String role)
    {
        long profileId = jdbc.queryForObject(
                "INSERT INTO user_profiles (first_name, last_name) VALUES ('Order', 'Tester') RETURNING id",
                Long.class);
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, role_id, profile_id)
                VALUES (?, 'not-used', (SELECT id FROM roles WHERE name = ?), ?) RETURNING id
                """, Long.class, email, role, profileId);
    }

    protected long customer()
    {
        return user("customer@example.test", "CUSTOMER");
    }

    protected long item(long menuId, String name, String price)
    {
        long id = item(menuId, name, true);
        jdbc.update("UPDATE menu_items SET price = ? WHERE id = ?", new BigDecimal(price), id);
        return id;
    }

    // Counts every row placing an order writes, so a refused order can be shown to
    // leave nothing behind.
    protected long orderRows()
    {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM orders) + (SELECT count(*) FROM order_lines)
                     + (SELECT count(*) FROM order_status_changes) + (SELECT count(*) FROM addresses)
                     + (SELECT count(*) FROM postal_codes)
                """, Long.class);
    }

    protected static Map<String, Object> line(long menuItemId, int quantity)
    {
        Map<String, Object> line = new HashMap<>();
        line.put("menuItemId", menuItemId);
        line.put("quantity", quantity);
        return line;
    }

    protected static Map<String, Object> deliveryAddress()
    {
        Map<String, Object> address = new HashMap<>();
        address.put("street", "Nørregade 10");
        address.put("postalCode", "1165");
        address.put("city", "København K");
        return address;
    }

    protected static Map<String, Object> placeOrder(List<Map<String, Object>> lines)
    {
        Map<String, Object> request = new HashMap<>();
        request.put("lines", new ArrayList<>(lines));
        request.put("deliveryAddress", deliveryAddress());
        request.put("paymentToken", "tok_visa");
        return request;
    }
}
