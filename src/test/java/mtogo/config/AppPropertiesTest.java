package mtogo.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasItem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

class AppPropertiesTest
{
    // Binds application.properties without Spring Boot's config data loading, so
    // neither a local .env file nor the developer's own environment variables can
    // change the result.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> isolateFromEnvironment(context.getEnvironment().getPropertySources()))
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(AppPropertiesConfiguration.class);

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/config/app-properties/bind-auth-enabled.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Auth enabled - binds only boolean values of FEATURE_AUTH_ENABLED")
    void authEnabledBindsOnlyBooleanValues(String featureAuthEnabled, String expected, String reason)
    {
        ApplicationContextRunner runner = featureAuthEnabled == null
                ? contextRunner
                : contextRunner.withPropertyValues("FEATURE_AUTH_ENABLED=" + featureAuthEnabled);

        runner.run(context -> {
            if (expected.equals("true") || expected.equals("false"))
            {
                assertThat(context.getBean(AppProperties.class).isAuthEnabled(), is(Boolean.parseBoolean(expected)));
            } else
            {
                assertThat(causeNames(context.getStartupFailure()), hasItem(expected));
            }
        });
    }

    // The binding failure is wrapped by the bean creation failure, so look through
    // the whole chain.
    private static List<String> causeNames(Throwable startupFailure)
    {
        List<String> names = new ArrayList<>();
        for (Throwable cause = startupFailure; cause != null; cause = cause.getCause())
        {
            names.add(cause.getClass().getSimpleName());
        }
        return names;
    }

    private static void isolateFromEnvironment(MutablePropertySources propertySources)
    {
        propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        try
        {
            propertySources.addLast(new ResourcePropertySource("classpath:application.properties"));
        } catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class AppPropertiesConfiguration
    {
    }
}
