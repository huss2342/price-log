package app.pricelog.api.security;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One person using the app. The row seeded by V7 ('owner@local') is the
 * pre-accounts single user; legacy X-API-Key traffic maps to the lowest-id
 * row, which is that owner on any database that went through the migration.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalized to lowercase before it is stored; the unique index is on this form. */
    @Column(nullable = false, length = 254, unique = true)
    private String email;

    /** BCrypt hash. Null only for the seeded owner row, which authenticates by API key. */
    @Column(name = "password_hash", length = 128)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
