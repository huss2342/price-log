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
 * Single-user shared-secret gate. The app is not multi-tenant, so a rotatable
 * secret in a header is the whole authentication story.
 *
 * <p>The secret is only ever read from the header. The photo endpoint used to
 * also accept it as a query parameter, so that an {@code <img>} tag could point
 * straight at it -- but that wrote the secret into browser history and into
 * every access log on the path. The front end fetches photo bytes and builds an
 * object URL instead.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String expectedKey;

    public ApiKeyFilter(PriceLogProperties properties) {
        this.expectedKey = properties.getAuth().getApiKey();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Health is public so Container Apps probes work without the secret.
        return expectedKey.isBlank()
                || path.startsWith("/actuator/health")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!matches(request.getHeader("X-API-Key"))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing or invalid X-API-Key\"}");
            return;
        }

        chain.doFilter(request, response);
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
