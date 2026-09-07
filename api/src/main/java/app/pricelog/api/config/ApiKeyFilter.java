package app.pricelog.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Splits the API into a public read side and a private write side.
 *
 * <p>Reading is open so the log can be shown to people. Everything that costs
 * money is not: a capture spends an Azure OpenAI call per photo, and forcing a
 * deals refresh spends an upstream scrape, so both need the key. So does every
 * mutation, since a public log nobody can edit is a showcase and a public log
 * anybody can edit is a liability.
 *
 * <p>The secret is only ever read from the header. The photo endpoint used to
 * also accept it as a query parameter, so an {@code <img>} could point straight
 * at it -- but that wrote the secret into browser history and into every access
 * log on the path. The front end fetches photo bytes and builds an object URL.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String expectedKey;
    private final AnonymousRateLimiter rateLimiter;

    public ApiKeyFilter(PriceLogProperties properties, AnonymousRateLimiter rateLimiter) {
        this.expectedKey = properties.getAuth().getApiKey();
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Health is public so Container Apps probes work without the secret.
        return expectedKey.isBlank()
                || request.getRequestURI().startsWith("/actuator/health")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (matches(request.getHeader("X-API-Key"))) {
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

    /**
     * A read is public when it neither changes state nor spends anything.
     * {@code force=true} on the deals endpoint fails both tests: it is a GET,
     * but it triggers a fresh scrape of Costco's site.
     */
    private boolean isPublicRead(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
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
