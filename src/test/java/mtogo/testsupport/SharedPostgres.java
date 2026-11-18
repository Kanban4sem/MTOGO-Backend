package mtogo.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container for the whole test run. It starts the first time a
 * test needs a database and is removed by Testcontainers when the JVM exits, so
 * no Spring context ever outlives the database it points at.
 */
public final class SharedPostgres
{
    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:17-alpine")
            // Each cached context keeps its pool open until the JVM exits.
            .withCommand("postgres", "-c", "max_connections=500");
    private static final Set<String> CREATED = new HashSet<>();

    private SharedPostgres()
    {
    }

    /**
     * Creates the database once in the shared container and returns the Spring
     * properties that point a context at it.
     */
    public static synchronized List<String> datasourceProperties(String database)
    {
        start();
        if (CREATED.add(database))
        {
            try (Connection connection = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
                    CONTAINER.getPassword()); var statement = connection.createStatement())
            {
                statement.execute("CREATE DATABASE \"" + database + "\"");
            } catch (SQLException e)
            {
                throw new IllegalStateException("Could not create test database " + database, e);
            }
        }
        return List.of(
                "spring.datasource.url=jdbc:postgresql://" + CONTAINER.getHost() + ":"
                        + CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database,
                "spring.datasource.username=" + CONTAINER.getUsername(),
                "spring.datasource.password=" + CONTAINER.getPassword());
    }

    // Started on first use rather than in a static initializer, so a missing Docker
    // fails every test class with this message instead of a NoClassDefFoundError.
    private static void start()
    {
        if (CONTAINER.isRunning())
        {
            return;
        }
        try
        {
            CONTAINER.start();
        } catch (RuntimeException e)
        {
            throw new IllegalStateException("Could not start the shared PostgreSQL test container. Is Docker running?",
                    e);
        }
    }
}
