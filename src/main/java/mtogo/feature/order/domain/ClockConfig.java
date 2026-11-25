package mtogo.feature.order.domain;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The dashboard takes "now" from this clock, so tests can fix the time.
@Configuration
public class ClockConfig
{
    @Bean
    public Clock clock()
    {
        return Clock.systemUTC();
    }
}
