package app.pricelog.api.security;

import java.util.Optional;

/**
 * Who the current request is acting as. Set by the auth filters and cleared
 * when the request completes, so a pooled thread can never leak one request's
 * identity into the next.
 *
 * <p>Absent on anonymous public-read GETs and on /actuator/health: those are
 * the only requests that reach past the filters without an identity.
 */
public final class CurrentUser {

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private final Long id;
    private final String email;

    public CurrentUser(Long id, String email) {
        this.id = id;
        this.email = email;
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    /** The identity the auth filters established for this request, if any. */
    public static Optional<CurrentUser> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * For the auth filters only. Anything else that needs the user goes
     * through {@code UserContext}.
     */
    public static void set(CurrentUser user) {
        CURRENT.set(user);
    }

    /** For the auth filters only: called when the request completes. */
    public static void clear() {
        CURRENT.remove();
    }
}
