package mtogo.feature.order.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockSmsProvider implements ISmsProvider
{
    @Override
    public void send(String phoneNumber, String message)
    {
        log.debug("Simulated order status SMS: {}", message);
    }
}
