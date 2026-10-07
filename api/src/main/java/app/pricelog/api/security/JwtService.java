package app.pricelog.api.security;

import app.pricelog.api.config.PriceLogProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies the Bearer tokens. HS256 with a single shared secret,
 * which is all a one-household app needs; the subject is the user's id and
 * nothing else sensitive goes into the claims.
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** The value application.yml falls back to when JWT_SECRET is unset. */
    static final String DEV_DEFAULT_SECRET = "price-log-dev-secret-change-me";

    /** A month: long enough that the PWA rarely asks you to sign in again. */
    private static final Duration EXPIRY = Duration.ofDays(30);

    private final SecretKey key;
    private final boolean devDefaultInUse;

    public JwtService(PriceLogProperties properties) {
        String secret = properties.getAuth().getJwtSecret();
        if (secret == null || secret.isBlank()) {
            secret = DEV_DEFAULT_SECRET;
        }
        this.devDefaultInUse = DEV_DEFAULT_SECRET.equals(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes(secret));
    }

    /**
     * HS256 needs at least 256 bits of key. Long secrets are used as-is; a
     * short one (including the dev default) is stretched deterministically
     * with SHA-256 rather than failing startup or signing weakly.
     */
    private static byte[] keyBytes(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return raw;
        }
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Warns once at startup when the token secret is the dev default. The
     * value itself is never logged.
     */
    @PostConstruct
    void warnOnDevSecret() {
        if (devDefaultInUse) {
            log.warn("JWT_SECRET is not set; signing tokens with the insecure dev default. "
                    + "Set JWT_SECRET to a long random value in production.");
        }
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(EXPIRY)))
                .signWith(key)
                .compact();
    }

    /**
     * @return the user id from the token's subject
     * @throws JwtException when the token is malformed, expired, or signed wrong
     */
    public Long userIdOf(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new JwtException("Token subject is not a user id", e);
        }
    }
}
