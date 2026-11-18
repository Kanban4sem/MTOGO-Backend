package mtogo.feature.user.presentation;

import mtogo.config.AppProperties;
import mtogo.security.FeatureGate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserGateConfig
{
    @Bean
    public FeatureGate authenticationGate(AppProperties properties)
    {
        return new FeatureGate("/v1/users", properties::isAuthEnabled, "Authentication is not available");
    }
}
