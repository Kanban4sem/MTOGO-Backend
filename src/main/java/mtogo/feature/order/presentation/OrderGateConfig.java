package mtogo.feature.order.presentation;

import mtogo.config.AppProperties;
import mtogo.security.FeatureGate;
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

    @Bean
    public FeatureGate customerOrdersGate(AppProperties properties)
    {
        return new FeatureGate("/v1/customer/orders", properties::isOrderingEnabled, "Ordering is not available");
    }

    @Bean
    public FeatureGate restaurantOrdersGate(AppProperties properties)
    {
        return new FeatureGate("/v1/restaurant/orders", properties::isOrderingEnabled, "Ordering is not available");
    }

    @Bean
    public FeatureGate deliveryAgentOrdersGate(AppProperties properties)
    {
        return new FeatureGate("/v1/delivery-agent/orders", properties::isOrderingEnabled, "Ordering is not available");
    }
}
