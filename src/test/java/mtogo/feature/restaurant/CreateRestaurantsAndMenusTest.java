package mtogo.feature.restaurant;

import mtogo.testsupport.SharedDatabase;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Tests the migration db/migration/V2__create_restaurants_and_menus.sql, which has no
// Java class, so the class is named after the file.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SharedDatabase
class CreateRestaurantsAndMenusTest
{
    @Autowired
    JdbcTemplate jdbc;

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/restaurant/create-restaurants-and-menus/save-price.csv", numLinesToSkip = 1)
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
