package mtogo.feature.order.presentation;

import mtogo.config.AppProperties;
import mtogo.featureflag.presentation.FeatureGate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderGateConfig
{
    @Bean
    public FeatureGate orderingGate(AppProperties properties)
    {
        return new FeatureGate("/v1/orders", properties::isOrderingEnabled, "Ordering is not available");
    }
}
