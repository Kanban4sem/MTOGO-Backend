package mtogo.exceptions;

import org.springframework.http.HttpStatus;

public class PaymentRequiredException extends ApiException
{
    public PaymentRequiredException(String detail)
    {
        this(detail, null);
    }

    public PaymentRequiredException(String detail, Throwable cause)
    {
        super(HttpStatus.PAYMENT_REQUIRED, detail, cause);
    }
}
