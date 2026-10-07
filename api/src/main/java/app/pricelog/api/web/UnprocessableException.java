package app.pricelog.api.web;

/**
 * The request was understood but its content fails validation: a blank label,
 * an unknown enum value, a weak password. 422 rather than 400, so callers can
 * tell "fix what you sent" apart from "malformed request".
 */
public class UnprocessableException extends RuntimeException {

    public UnprocessableException(String message) {
        super(message);
    }
}
