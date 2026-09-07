package app.pricelog.api.web;

import app.pricelog.api.capture.UnreadableTagException;
import app.pricelog.api.extract.TagExtractionException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** The photo was processed but held no readable price. Worth retrying with a better shot. */
    @ExceptionHandler(UnreadableTagException.class)
    public ResponseEntity<Map<String, String>> unreadable(UnreadableTagException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(TagExtractionException.class)
    public ResponseEntity<Map<String, String>> extraction(TagExtractionException e) {
        log.warn("Extraction failed", e);
        return error(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "That photo is too large. Keep uploads under 15 MB.");
    }

    /** The request was well formed but conflicts with the current state. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Anything not handled above.
     *
     * <p>Spring's own MVC exceptions are separated out first. They already
     * carry the right status -- a missing multipart part is a 400, not a server
     * fault -- and relabelling them as 500 buries a caller's mistake in the
     * error log. They implement {@link ErrorResponse} but share no common
     * supertype, so the split is a type check rather than another handler.
     *
     * <p>Everything past that is genuinely unexpected, and without this it
     * returns an unshaped container error with no stack trace, which is what
     * once made a capture that recorded nothing effectively undiagnosable.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        if (e instanceof ErrorResponse spring) {
            HttpStatus status = HttpStatus.valueOf(spring.getStatusCode().value());
            log.debug("Rejecting a malformed request: {}", status);
            return error(status, spring.getBody().getDetail());
        }
        log.error("Unhandled failure", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong handling that request. The details are in the server log.");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? status.getReasonPhrase() : message));
    }
}
