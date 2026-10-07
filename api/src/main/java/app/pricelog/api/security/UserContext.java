package app.pricelog.api.security;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves which user's data a request operates on. Controllers and services
 * ask this instead of reading the thread-local directly, so the "who" rule
 * lives in one place.
 *
 * <p>The rule: an authenticated request acts as itself; an anonymous
 * public-read GET (or a call with no request at all, as in tests) acts as the
 * owner, whose log is the published one.
 */
@Component
public class UserContext {

    private final UserRepository users;

    public UserContext(UserRepository users) {
        this.users = users;
    }

    /** The identity the auth filters established, if the request carried credentials. */
    public Optional<CurrentUser> current() {
        return CurrentUser.current();
    }

    /**
     * The user id whose data this request reads or writes: the authenticated
     * user, falling back to the owner for anonymous public reads.
     */
    public Long userId() {
        return current().map(CurrentUser::id).orElseGet(this::ownerId);
    }

    /**
     * The authenticated user. Endpoints that must never serve anonymous
     * traffic (account, export) call this instead of {@link #userId()}.
     *
     * @throws ResponseStatusException 401 when the request carries no credentials
     */
    public CurrentUser require() {
        return current().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Sign in to continue."));
    }

    private Long ownerId() {
        return users.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No users exist"))
                .getId();
    }
}
