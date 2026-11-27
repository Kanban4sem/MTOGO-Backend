package mtogo.feature.order;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mtogo.feature.order.domain.OrderStatus;
import mtogo.feature.restaurant.RestaurantTestData;
import org.junit.jupiter.api.BeforeEach;

public abstract class OrderTestData extends RestaurantTestData
{
    // Overrides the restaurant cleanup, because order lines and restaurant managers
    // must be deleted before the menu items and restaurants they reference.
    @BeforeEach
    @Override
    protected void cleanDatabase()
    {
        jdbc.update("DELETE FROM order_status_changes");
        jdbc.update("DELETE FROM order_lines");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM addresses");
        jdbc.update("DELETE FROM postal_codes");
        super.cleanDatabase();
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

    protected long restaurantManager(String email, long restaurantId)
    {
        long id = user(email, "RESTAURANT");
        jdbc.update("UPDATE users SET restaurant_id = ? WHERE id = ?", restaurantId, id);
        return id;
    }

    protected long deliveryAgent(String name)
    {
        return user(name + "@example.test", "DELIVERY_AGENT");
    }

    // Moves an order straight to a status and delivery agent, without the status
    // change a real move records.
    protected void setOrder(long orderId, String status, Long deliveryAgentId)
    {
        BigDecimal amount = deliveredAmount(status);
        jdbc.update("UPDATE orders SET status = ?, delivery_agent_id = ?, commission = ?, service_fee = ? WHERE id = ?",
                status, deliveryAgentId, amount, amount, orderId);
    }

    // Inserts an order with a status change at each given time, so a test controls
    // exactly when each step ended. The current status is the latest in the
    // lifecycle, which an EnumMap iterates last.
    protected long orderAt(long restaurantId, long customerId, String total, EnumMap<OrderStatus, Instant> statusTimes)
    {
        OrderStatus currentStatus = statusTimes.keySet().stream().reduce((earlier, later) -> later).orElseThrow();
        long postalCodeId = jdbc.queryForObject(
                "INSERT INTO postal_codes (code, city, country) VALUES ('1165', 'Copenhagen', 'Denmark') RETURNING id",
                Long.class);
        long addressId = jdbc.queryForObject(
                "INSERT INTO addresses (street, postal_code_id, is_default) VALUES ('Test Street 10', ?, false) RETURNING id",
                Long.class, postalCodeId);
        BigDecimal amount = deliveredAmount(currentStatus.name());
        long orderId = jdbc.queryForObject("""
                INSERT INTO orders (customer_id, restaurant_id, delivery_address_id, channel, total, status,
                    commission, service_fee)
                VALUES (?, ?, ?, 'WEBSITE', ?, ?, ?, ?) RETURNING id
                """, Long.class, customerId, restaurantId, addressId, new BigDecimal(total), currentStatus.name(),
                amount, amount);
        statusTimes.forEach((status, changedAt) -> jdbc.update(
                "INSERT INTO order_status_changes (order_id, status, changed_at) VALUES (?, ?, ?)", orderId,
                status.name(), Timestamp.from(changedAt)));
        return orderId;
    }

    protected String readStatus(long orderId)
    {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    protected Long readDeliveryAgent(long orderId)
    {
        return jdbc.queryForObject("SELECT delivery_agent_id FROM orders WHERE id = ?", Long.class, orderId);
    }

    protected List<String> orderHistory(long orderId)
    {
        return jdbc.queryForList("SELECT status FROM order_status_changes WHERE order_id = ? ORDER BY id", String.class,
                orderId);
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

    // The database requires a commission and service fee on a delivered order and
    // refuses them on any other, so a delivered test order gets 0.00 for both.
    private static BigDecimal deliveredAmount(String status)
    {
        return OrderStatus.DELIVERED.name().equals(status) ? BigDecimal.ZERO : null;
    }
}
