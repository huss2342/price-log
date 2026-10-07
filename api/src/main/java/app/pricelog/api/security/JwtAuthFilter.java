package app.pricelog.api.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer-token authentication. Runs before {@code ApiKeyFilter}: a valid JWT
 * establishes the user for the request, and a bad one is rejected outright
 * rather than falling through to the API-key check, so a forged token can
 * never be mistaken for an anonymous visitor.
 *
 * <p>Whatever identity this filter sets is cleared in the finally block, so
 * the thread-local cannot outlive the request even when the chain throws.
 */
@Component
@Order(1)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwt;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                String token = header.substring(7).trim();
                try {
                    Long userId = jwt.userIdOf(token);
                    AppUser user = users.findById(userId).orElseThrow(
                            () -> new JwtException("Unknown user in token"));
                    CurrentUser.set(new CurrentUser(user.getId(), user.getEmail()));
                } catch (JwtException | IllegalArgumentException e) {
                    log.debug("Rejecting a bad bearer token: {}", e.getMessage());
                    deny(response, "That sign-in has expired or is not valid. Sign in again.");
                    return;
                }
            }
            chain.doFilter(request, response);
        } finally {
            CurrentUser.clear();
        }
    }

    private void deny(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
