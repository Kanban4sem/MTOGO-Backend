package mtogo.feature.order.domain;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderStatusSmsSender
{
    private final ISmsProvider sms;

    public OrderStatusSmsSender(ISmsProvider sms)
    {
        this.sms = sms;
    }

    @TransactionalEventListener
    public void send(OrderStatusSms notification)
    {
        sms.send(notification.phoneNumber(), notification.message());
    }

    record OrderStatusSms(String phoneNumber, String message)
    {
    }
}
