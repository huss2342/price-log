package app.pricelog.api.config;

import app.pricelog.api.security.CurrentUser;
import app.pricelog.api.security.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Splits the API into a public read side and a private write side. Runs after
 * {@code JwtAuthFilter}, so a request that already proved itself with a Bearer
 * token keeps that identity; this filter only speaks for the legacy key.
 *
 * <p>A valid {@code X-API-Key} acts as the owner (the first user): that is
 * what the deployed setup link and demo mode authenticate with. When the key
 * is blank -- the local development default -- the filter stays out of the way
 * and every request acts as the owner, preserving today's frictionless dev loop.
 *
 * <p>Reading is closed by default. The showcase is served by the front end's
 * own sample data, so opening the real log buys nothing and exposes a
 * household's shopping and its tag photos. Setting
 * {@code pricelog.auth.public-read} opens every GET for a deployment that does
 * want to publish. Anonymous public reads are rate-limited and carry no
 * identity; downstream code falls back to the owner's data for them.
 *
 * <p>Even then, what costs money stays shut: a capture spends an Azure OpenAI
 * call per photo, and forcing a deals refresh spends an upstream scrape, so
 * both need the key. So does every mutation, since a public log nobody can edit
 * is a showcase and a public log anybody can edit is a liability.
 *
 * <p>The secret is only ever read from the header. The photo endpoint used to
 * also accept it as a query parameter, so an {@code <img>} could point straight
 * at it -- but that wrote the secret into browser history and into every access
 * log on the path. The front end fetches photo bytes and builds an object URL.
 */
@Component
@Order(2)
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String expectedKey;
    private final boolean publicRead;
    private final AnonymousRateLimiter rateLimiter;
    private final UserRepository users;

    public ApiKeyFilter(PriceLogProperties properties, AnonymousRateLimiter rateLimiter,
                        UserRepository users) {
        this.expectedKey = properties.getAuth().getApiKey();
        this.publicRead = properties.getAuth().isPublicRead();
        this.rateLimiter = rateLimiter;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Health is public so Container Apps probes work without the secret.
        if (uri.startsWith("/actuator/health") || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // Register and login are the front door: they cannot ask for credentials.
        return isAuthEndpoint(uri) && "POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (CurrentUser.current().isPresent()) {
            // JwtAuthFilter already authenticated this request with a Bearer
            // token. The API key is the legacy path; it is not needed on top.
            chain.doFilter(request, response);
            return;
        }

        if (expectedKey.isBlank()) {
            // Development default: no key configured, so everyone is the owner.
            // A Bearer token that JwtAuthFilter already accepted is left alone.
            setOwnerIfAnonymous();
            chain.doFilter(request, response);
            return;
        }

        if (matches(request.getHeader("X-API-Key"))) {
            setOwnerIfAnonymous();
            chain.doFilter(request, response);
            return;
        }

        if (!isPublicRead(request)) {
            deny(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "This action needs the owner's API key.");
            return;
        }

        if (!rateLimiter.tryAcquire(clientOf(request))) {
            deny(response, 429, "Too many requests. Slow down and try again shortly.");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(String uri) {
        return uri.equals("/api/auth/register") || uri.equals("/api/auth/login");
    }

    /**
     * Gives the request the owner's identity unless JwtAuthFilter already set
     * a better one (a Bearer token outranks the shared key).
     */
    private void setOwnerIfAnonymous() {
        if (CurrentUser.current().isPresent()) {
            return;
        }
        users.findFirstByOrderByIdAsc().ifPresent(
                owner -> CurrentUser.set(new CurrentUser(owner.getId(), owner.getEmail())));
    }

    /**
     * A read is public when it neither changes state nor spends anything.
     * {@code force=true} on the deals endpoint fails both tests: it is a GET,
     * but it triggers a fresh scrape of Costco's site.
     */
    private boolean isPublicRead(HttpServletRequest request) {
        if (!publicRead || !"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return !Boolean.parseBoolean(request.getParameter("force"));
    }

    /**
     * Container Apps terminates TLS in front of the container, so the socket
     * address is the ingress, not the visitor. The first hop of the forwarded
     * chain is the closest thing to a real client address available here.
     */
    private String clientOf(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private void deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    /** Constant-time comparison so the secret cannot be recovered by timing. */
    private boolean matches(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expectedKey.getBytes(StandardCharsets.UTF_8));
    }
}
