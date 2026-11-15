package mtogo.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public abstract class ApiException extends ErrorResponseException
{
    protected ApiException(HttpStatus status, String detail, Throwable cause)
    {
        super(status, ProblemDetail.forStatusAndDetail(status, detail), cause);
    }
}
