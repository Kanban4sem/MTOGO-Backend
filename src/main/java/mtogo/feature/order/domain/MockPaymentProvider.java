package mtogo.feature.order.domain;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Stands in for the outside payment supplier, which MTOGO doesn't integrate
// with. The token "decline" lets testers and the frontend try a declined payment.
@Component
public class MockPaymentProvider implements IPaymentProvider
{
    static final String DECLINE_TOKEN = "decline";

    @Override
    public PaymentResult charge(BigDecimal total, String paymentToken)
    {
        if (!StringUtils.hasText(paymentToken) || paymentToken.equals(DECLINE_TOKEN))
        {
            return PaymentResult.declined();
        }
        return PaymentResult.confirmed("mock-" + UUID.randomUUID());
    }
}
