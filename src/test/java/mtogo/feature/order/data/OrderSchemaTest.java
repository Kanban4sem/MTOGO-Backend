package mtogo.feature.order.data;

import static org.junit.jupiter.api.Assertions.*;

import mtogo.feature.order.OrderTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Without a test transaction, so a refused delete doesn't abort the cleanup
// that OrderTestData runs after each test.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderSchemaTest extends OrderTestData
{
    @Test
    @DisplayName("Delete menu item - the database refuses to delete a menu item that has been ordered")
    void orderedMenuItemCannotBeDeleted()
    {
        long menuId = menu(restaurant("Schema test", true), "Schema test", true);
        long orderedItemId = orderedMenuItem(menuId);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM menu_items WHERE id = ?", orderedItemId));
    }

    @Test
    @DisplayName("Delete menu item - a menu item that has never been ordered can still be deleted")
    void unorderedMenuItemCanBeDeleted()
    {
        long menuId = menu(restaurant("Schema test", true), "Schema test", true);
        orderedMenuItem(menuId);
        long unorderedItemId = item(menuId, "Unordered", true);

        assertEquals(1, jdbc.update("DELETE FROM menu_items WHERE id = ?", unorderedItemId));
    }

    // Inserts one placed order with one order line on the menu and returns the
    // ordered menu item.
    private long orderedMenuItem(long menuId)
    {
        long itemId = item(menuId, "Meal", true);
        long restaurantId = jdbc.queryForObject("SELECT restaurant_id FROM menus WHERE id = ?", Long.class, menuId);
        long postalCodeId = jdbc.queryForObject(
                "INSERT INTO postal_codes (code, city, country) VALUES ('2100', 'Copenhagen', 'Denmark') RETURNING id",
                Long.class);
        long addressId = jdbc.queryForObject(
                "INSERT INTO addresses (street, postal_code_id, is_default) VALUES ('Main Street 1', ?, false) RETURNING id",
                Long.class, postalCodeId);
        long orderId = jdbc.queryForObject("""
                INSERT INTO orders (customer_id, restaurant_id, delivery_address_id, channel, total, status)
                VALUES (?, ?, ?, 'WEBSITE', 89.95, 'PLACED') RETURNING id
                """, Long.class, customer(), restaurantId, addressId);
        jdbc.update(
                "INSERT INTO order_lines (order_id, menu_item_id, name, unit_price, quantity) VALUES (?, ?, 'Meal', 89.95, 1)",
                orderId, itemId);
        return itemId;
    }
}
