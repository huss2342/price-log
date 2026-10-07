package app.pricelog.api.web;

/**
 * Authenticated, but not allowed: the current password did not match, the row
 * belongs to someone else. Distinct from 401, which means "not signed in".
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
