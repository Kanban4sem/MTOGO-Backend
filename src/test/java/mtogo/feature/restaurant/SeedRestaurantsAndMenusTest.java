package mtogo.feature.restaurant;

import mtogo.testsupport.SharedDatabase;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

// Tests the migration db/migration/V3__seed_restaurants_and_menus.sql, which has no
// Java class, so the class is named after the file.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SharedDatabase
class SeedRestaurantsAndMenusTest
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
}
