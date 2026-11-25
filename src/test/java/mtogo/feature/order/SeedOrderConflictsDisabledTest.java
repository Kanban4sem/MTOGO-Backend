package mtogo.feature.order;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import mtogo.testsupport.SharedDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Tests the SQL seed db/local/afterMigrate__seed_order_conflicts.sql, which has no
// Java class, so the class is named after the file.
@SharedDatabase
@ActiveProfiles("default")
@SpringBootTest
class SeedOrderConflictsDisabledTest
{
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Startup - the default profile omits the local order conflict seed data")
    void leavesOutOrderConflictSeedData()
    {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM restaurants WHERE name = 'MTOGO Closed Restaurant'",
                Long.class), is(0L));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM menu_items WHERE name IN ('Sold-out kebab', 'Closed restaurant meal')",
                Long.class), is(0L));
    }
}
