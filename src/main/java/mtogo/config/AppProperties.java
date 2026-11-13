package mtogo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
// A feature flag keeps its property name without the is prefix, as in
// app.auth-enabled.
public record AppProperties(@Valid @NotNull Cors cors, @Name("auth-enabled") boolean isAuthEnabled)
{
    public record Cors(@NotBlank String allowedOrigin)
    {
    }

}