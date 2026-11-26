package mtogo.feature.commission;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.sql.DataSource;
import mtogo.feature.order.OrderTestData;
import mtogo.feature.order.domain.OrderStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Tests the migration db/migration/V10__add_commission_scales.sql, which has no
// Java class, so the class is named after the file.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Without a test transaction, so a refused update doesn't abort the transaction
// that the test data and the cleanup run in.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AddCommissionScalesTest extends OrderTestData
{
    // The test data resets the scales before each test, and the backfill needs
    // orders that exist before the migration. So the migration is checked in a
    // schema of its own, migrated first to the version before it.
    private static final String SCHEMA = "add_commission_scales";
    private static final String PREVIOUS_VERSION = "9";
    private static final String VERSION = "10";

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("Migrate - seeds the default scale as the only sales tier")
    void seedsTheDefaultTier() throws SQLException
    {
        inOwnSchema(schema -> {
            migrate(schema, VERSION);

            assertThat(schema.queryForList("SELECT minimum_sales, service_fee, restaurant_id FROM commission_scales"),
                    contains(allOf(hasEntry("minimum_sales", (Object) new BigDecimal("0.00")),
                            hasEntry("service_fee", (Object) new BigDecimal("5.00")),
                            hasEntry("restaurant_id", null))));
            assertThat(schema.queryForList(
                    "SELECT lower_limit || ':' || rate_percent FROM commission_brackets ORDER BY lower_limit",
                    String.class), contains("0.00:6.00", "100.00:5.00", "500.00:4.00", "1000.00:3.00"));
        });
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvFileSource(resources = "/testcases/commission/add-commission-scales/migrate-backfill.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Migrate - gives orders already delivered the default scale's amounts, and other orders none")
    void backfillsDeliveredOrders(String status, BigDecimal total, BigDecimal expectedCommission,
            BigDecimal expectedServiceFee, String reason) throws SQLException
    {
        inOwnSchema(schema -> {
            migrate(schema, PREVIOUS_VERSION);
            long restaurantId = schema.queryForObject("SELECT min(id) FROM restaurants", Long.class);
            long orderId = orderBefore(schema, customerBefore(schema), restaurantId, status, total);

            migrate(schema, VERSION);

            Map<String, Object> amounts = schema.queryForMap("SELECT commission, service_fee FROM orders WHERE id = ?",
                    orderId);
            assertThat(amounts.get("commission"), is(expectedCommission));
            assertThat(amounts.get("service_fee"), is(expectedServiceFee));
        });
    }

    @Test
    @DisplayName("Migrate - the database refuses amounts on an order that isn't delivered, and a delivered order without them")
    void amountsOnlyOnDeliveredOrders()
    {
        long placedId = orderAt(restaurant("Bakery", true), customer(), "125.00",
                new EnumMap<>(Map.of(OrderStatus.PLACED, Instant.parse("2026-11-25T12:00:00Z"))));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE orders SET commission = 6.00, service_fee = 5.00 WHERE id = ?", placedId));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE orders SET status = 'DELIVERED' WHERE id = ?", placedId));
    }

    // Runs the check on one connection whose search path is the schema, and drops
    // the schema afterwards.
    private void inOwnSchema(Consumer<JdbcTemplate> check) throws SQLException
    {
        try (Connection connection = dataSource.getConnection())
        {
            JdbcTemplate schema = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            try
            {
                check.accept(schema);
            } finally
            {
                schema.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
                schema.execute("RESET search_path");
            }
        }
    }

    private static void migrate(JdbcTemplate schema, String target)
    {
        Flyway.configure().dataSource(schema.getDataSource()).schemas(SCHEMA).locations("classpath:db/migration")
                .target(target).load().migrate();
        // Flyway restores the search path after migrating.
        schema.execute("SET search_path TO " + SCHEMA);
    }

    private static long customerBefore(JdbcTemplate schema)
    {
        long profileId = schema.queryForObject(
                "INSERT INTO user_profiles (first_name, last_name) VALUES ('Backfill', 'Tester') RETURNING id",
                Long.class);
        return schema.queryForObject("""
                INSERT INTO users (email, password_hash, role_id, profile_id)
                VALUES ('backfill@example.test', 'not-used', (SELECT id FROM roles WHERE name = 'CUSTOMER'), ?)
                RETURNING id
                """, Long.class, profileId);
    }

    private static long orderBefore(JdbcTemplate schema, long customerId, long restaurantId, String status,
            BigDecimal total)
    {
        long postalCodeId = schema.queryForObject(
                "INSERT INTO postal_codes (code, city, country) VALUES ('1165', 'Copenhagen', 'Denmark') RETURNING id",
                Long.class);
        long addressId = schema.queryForObject(
                "INSERT INTO addresses (street, postal_code_id, is_default) VALUES ('Test Street 10', ?, false) RETURNING id",
                Long.class, postalCodeId);
        return schema.queryForObject("""
                INSERT INTO orders (customer_id, restaurant_id, delivery_address_id, channel, total, status)
                VALUES (?, ?, ?, 'WEBSITE', ?, ?) RETURNING id
                """, Long.class, customerId, restaurantId, addressId, total, status);
    }
}
