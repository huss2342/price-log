package app.pricelog.api.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link UserProductWatch}: one row per user and product. */
public class UserProductWatchId implements Serializable {

    private Long userId;
    private Long productId;

    public UserProductWatchId() {
    }

    public UserProductWatchId(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserProductWatchId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(productId, other.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, productId);
    }
}
