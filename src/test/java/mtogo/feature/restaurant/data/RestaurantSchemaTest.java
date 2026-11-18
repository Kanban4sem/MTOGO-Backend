package mtogo.feature.restaurant.data;

import mtogo.testsupport.SharedDatabase;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SharedDatabase
class RestaurantSchemaTest
{
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Initial catalogue - Flyway seeds two accepting restaurants with an active menu and available item each")
    void initialCatalogue()
    {
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM restaurants WHERE accepts_orders", Long.class));
        var rows = jdbc.queryForList("""
                SELECT r.name, i.price, i.image_url FROM restaurants r
                JOIN menus m ON m.restaurant_id = r.id AND m.is_active
                JOIN menu_items i ON i.menu_id = m.id AND i.is_available ORDER BY r.name
                """);
        assertEquals(2, rows.size());
        assertEquals("MTOGO Bakery", rows.getFirst().get("name"));
        assertEquals(new BigDecimal("25.00"), rows.getFirst().get("price"));
        assertEquals("/images/restaurant/menu/restaurant-2-item-1.jpg", rows.getFirst().get("image_url"));
        assertEquals("MTOGO Kebab", rows.getLast().get("name"));
        assertEquals(new BigDecimal("89.00"), rows.getLast().get("price"));
        assertEquals("/images/restaurant/menu/restaurant-1-item-1.jpg", rows.getLast().get("image_url"));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/restaurant/restaurant-schema/save-price.csv", numLinesToSkip = 1)
    @DisplayName("Save price - the database rejects negative DKK prices and preserves cents")
    void savePrice(BigDecimal price, String expected, String reason)
    {
        long restaurantId = jdbc.queryForObject(
                "INSERT INTO restaurants (name, accepts_orders) VALUES ('Price test', true) RETURNING id", Long.class);
        long menuId = jdbc.queryForObject(
                "INSERT INTO menus (restaurant_id, name) VALUES (?, 'Price test') RETURNING id", Long.class,
                restaurantId);
        org.junit.jupiter.api.function.Executable insert = () -> jdbc.update(
                "INSERT INTO menu_items (menu_id, name, price, image_url) VALUES (?, 'Meal', ?, '/meal.jpg')", menuId,
                price);
        if (expected.equals("ACCEPT"))
        {
            assertDoesNotThrow(insert);
            assertEquals(price,
                    jdbc.queryForObject("SELECT price FROM menu_items WHERE menu_id = ?", BigDecimal.class, menuId));
        } else
        {
            assertThrows(DataIntegrityViolationException.class, insert);
        }
    }
}
