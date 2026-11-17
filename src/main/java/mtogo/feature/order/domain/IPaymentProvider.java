package mtogo.feature.order.domain;

import java.math.BigDecimal;

// The MVP mocks payment. A real provider replaces the implementation without
// changing how an order is placed.
public interface IPaymentProvider
{
    /**
     * Charges the order total in DKK with the customer's payment token. Never
     * throws for a declined payment: it returns a declined result instead, and the
     * caller decides what a decline means.
     */
    PaymentResult charge(BigDecimal total, String paymentToken);
}
