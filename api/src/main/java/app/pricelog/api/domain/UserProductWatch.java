package app.pricelog.api.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One user's watch on one product. Moved off the shared {@code product} row by
 * the accounts migration: watching is personal, the catalog is not.
 */
@Entity
@Table(name = "user_product_watch")
@IdClass(UserProductWatchId.class)
public class UserProductWatch {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "product_id")
    private Long productId;

    /** Flagged for the alerts screen. */
    @Column(nullable = false)
    private boolean watched = true;

    /** Optional "tell me when it reaches this", in cents. */
    @Column(name = "target_price_cents")
    private Integer targetPriceCents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserProductWatch() {
    }

    public UserProductWatch(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public boolean isWatched() {
        return watched;
    }

    public void setWatched(boolean watched) {
        this.watched = watched;
    }

    public Integer getTargetPriceCents() {
        return targetPriceCents;
    }

    public void setTargetPriceCents(Integer targetPriceCents) {
        this.targetPriceCents = targetPriceCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
