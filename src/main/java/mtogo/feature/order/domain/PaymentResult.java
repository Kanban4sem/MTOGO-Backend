package mtogo.feature.order.domain;

// The reference is only set when the payment is confirmed.
public record PaymentResult(boolean isConfirmed, String reference)
{
    public static PaymentResult confirmed(String reference)
    {
        return new PaymentResult(true, reference);
    }

    public static PaymentResult declined()
    {
        return new PaymentResult(false, null);
    }
}
