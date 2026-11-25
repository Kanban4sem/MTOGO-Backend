package mtogo.config;

import jakarta.validation.Valid;
import mtogo.feature.role.domain.RoleName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
// A feature flag keeps its property name without the is prefix, as in
// app.auth-enabled.
public record AppProperties(@Valid @NotNull Cors cors, @Name("auth-enabled") boolean isAuthEnabled,
        @Name("ordering-enabled") boolean isOrderingEnabled, @NotNull Seed seed)
{
    public record Cors(@NotBlank String allowedOrigin)
    {
    }

    // A blank password skips that role's seeded users, so none is required here.
    public record Seed(String customerPassword, String restaurantPassword, String deliveryAgentPassword,
            String adminPassword)
    {
        public String passwordFor(RoleName role)
        {
            return switch (role)
            {
                case CUSTOMER -> customerPassword;
                case RESTAURANT -> restaurantPassword;
                case DELIVERY_AGENT -> deliveryAgentPassword;
                case ADMIN -> adminPassword;
            };
        }
    }

}