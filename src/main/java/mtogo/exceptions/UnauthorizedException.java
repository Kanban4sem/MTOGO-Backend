package mtogo.exceptions;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException
{
    public UnauthorizedException(String detail)
    {
        this(detail, null);
    }

    public UnauthorizedException(String detail, Throwable cause)
    {
        super(HttpStatus.UNAUTHORIZED, detail, cause);
    }
}
