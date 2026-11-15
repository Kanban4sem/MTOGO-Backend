package mtogo.exceptions;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException
{
    public NotFoundException(String detail)
    {
        this(detail, null);
    }

    public NotFoundException(String detail, Throwable cause)
    {
        super(HttpStatus.NOT_FOUND, detail, cause);
    }
}
