package mtogo.exceptions;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException
{
    public BadRequestException(String detail)
    {
        this(detail, null);
    }

    public BadRequestException(String detail, Throwable cause)
    {
        super(HttpStatus.BAD_REQUEST, detail, cause);
    }
}
