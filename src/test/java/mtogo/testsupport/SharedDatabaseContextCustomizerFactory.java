package mtogo.testsupport;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Points the Spring context of a {@link SharedDatabase} test class at its own
 * database in {@link SharedPostgres}. The database name is part of the context
 * cache key, so two test classes never share a context or a database. A nested
 * class uses the database of its top-level class, because its test methods use
 * the top-level instance's helpers and data.
 */
public class SharedDatabaseContextCustomizerFactory implements ContextCustomizerFactory
{
    // PostgreSQL silently truncates longer identifiers, but the JDBC URL would not.
    private static final int MAX_DATABASE_NAME_LENGTH = 63;

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes)
    {
        if (!TestContextAnnotationUtils.hasAnnotation(testClass, SharedDatabase.class))
        {
            return null;
        }
        return new SharedDatabaseContextCustomizer(databaseName(testClass.getNestHost()));
    }

    // The hash of the fully qualified name keeps classes with the same simple name
    // apart; the simple name only makes the database recognisable in pgAdmin.
    static String databaseName(Class<?> testClass)
    {
        String name = "test_" + Integer.toHexString(testClass.getName().hashCode()) + "_"
                + testClass.getSimpleName().toLowerCase();
        return name.substring(0, Math.min(name.length(), MAX_DATABASE_NAME_LENGTH));
    }

    private record SharedDatabaseContextCustomizer(String databaseName) implements ContextCustomizer
    {
        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration config)
        {
            List<String> properties = new ArrayList<>(SharedPostgres.datasourceProperties(databaseName));
            properties.add("spring.datasource.hikari.maximum-pool-size=5");
            TestPropertyValues.of(properties).applyTo(context);
        }
    }
}
