package mtogo.exceptions;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException
{
    public ConflictException(String detail)
    {
        this(detail, null);
    }

    public ConflictException(String detail, Throwable cause)
    {
        super(HttpStatus.CONFLICT, detail, cause);
    }
}
