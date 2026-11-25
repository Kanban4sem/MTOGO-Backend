package mtogo.security;

import static mtogo.testsupport.CauseUtil.causeNames;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import mtogo.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

class JwtServiceTest
{
    // Binds application.properties without Spring Boot's config data loading, so
    // neither a local .env file nor the developer's own environment variables can
    // change the result.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> isolateFromEnvironment(context.getEnvironment().getPropertySources()))
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(JwtServiceConfiguration.class);

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvFileSource(resources = "/testcases/security/jwt-service/startup-lifetime.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Startup lifetime - starts only with a token lifetime from 1 to 86400 seconds")
    void startsOnlyWithLifetimeInRange(String jwtLifetimeSeconds, String expected, String reason)
    {
        Map<String, Object> variables = new HashMap<>();
        if (jwtLifetimeSeconds != null)
        {
            variables.put("JWT_LIFETIME_SECONDS", jwtLifetimeSeconds);
        }

        withVariables(variables).run(context -> {
            if (expected.matches("\\d+"))
            {
                long lifetimeSeconds = Long.parseLong(expected);
                Instant before = Instant.now();
                Instant expiresAt = context.getBean(JwtService.class).issue(1L, 0).expiresAt();
                Instant after = Instant.now();
                assertThat(expiresAt, allOf(greaterThanOrEqualTo(before.plusSeconds(lifetimeSeconds)),
                        lessThanOrEqualTo(after.plusSeconds(lifetimeSeconds))));
            } else
            {
                assertThat(causeNames(context.getStartupFailure()), hasItem(expected));
            }
        });
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvFileSource(resources = "/testcases/security/jwt-service/startup-secret.csv", numLinesToSkip = 1, nullValues = "NULL")
    @DisplayName("Startup secret - requires a signing secret only when authentication is enabled, and a set secret must be a Base64 key of at least 32 bytes")
    void requiresSecretOnlyWhenAuthEnabled(String featureAuthEnabled, String jwtSecret, String expected, String reason)
    {
        Map<String, Object> variables = new HashMap<>();
        variables.put("FEATURE_AUTH_ENABLED", featureAuthEnabled);
        if (jwtSecret != null)
        {
            variables.put("JWT_SECRET", jwtSecret);
        }

        withVariables(variables).run(context -> {
            if (expected.equals("ACCEPT"))
            {
                assertThat(context.getStartupFailure(), is(nullValue()));
                JwtService jwtService = context.getBean(JwtService.class);
                String token = jwtService.issue(1L, 0).value();
                assertThat(jwtService.verify(token).userId(), is(1L));
            } else
            {
                assertThat(causeNames(context.getStartupFailure()), hasItem(expected));
            }
        });
    }

    // withPropertyValues trims values, which would hide the blank secret.
    private ApplicationContextRunner withVariables(Map<String, Object> variables)
    {
        return contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("variables", variables)));
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
    @Import(JwtService.class)
    static class JwtServiceConfiguration
    {
    }
}
