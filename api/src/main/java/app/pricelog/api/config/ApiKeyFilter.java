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
 * <p>The photo endpoint also accepts the secret as a query parameter, because
 * an {@code <img>} tag cannot send headers.
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

        String presented = request.getHeader("X-API-Key");
        if (presented == null && request.getRequestURI().startsWith("/api/photos/")) {
            presented = request.getParameter("apiKey");
        }

        if (!matches(presented)) {
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
