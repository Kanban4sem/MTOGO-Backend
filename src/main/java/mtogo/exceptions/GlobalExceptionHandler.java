package mtogo.exceptions;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler
{
    // Shared with SecurityConfig, so a 403 looks the same whether the filter
    // chain or method security denies the request.
    public static final String ACCESS_DENIED = "Access is denied";

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException exception, WebRequest request)
    {
        LOG.warn("Request failed with {}: {}", exception.getStatusCode().value(), exception.getBody().getDetail());
        return handleErrorResponseException(exception, exception.getHeaders(), exception.getStatusCode(), request);
    }

    // Method security throws inside the controller call, so this handler sees
    // the denial before Spring Security's own 403 handler could.
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception)
    {
        LOG.warn("Request failed with 403: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ACCESS_DENIED);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConflict(DataIntegrityViolationException exception)
    {
        LOG.warn("A database constraint rejected the operation");
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception)
    {
        String errorId = UUID.randomUUID().toString();
        LOG.error("Unexpected failure {}", errorId, exception);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error");
        detail.setProperty("errorId", errorId);
        return detail;
    }
}
