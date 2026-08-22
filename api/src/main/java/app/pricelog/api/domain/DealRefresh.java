package app.pricelog.api.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Bookkeeping for one deal source, so a failing fetch is visible, not silent. */
@Entity
@Table(name = "deal_refresh")
public class DealRefresh {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Chain chain;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "deals_found")
    private Integer dealsFound;

    public DealRefresh() {
    }

    public DealRefresh(Chain chain) {
        this.chain = chain;
    }

    public Chain getChain() {
        return chain;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(Instant lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Integer getDealsFound() {
        return dealsFound;
    }

    public void setDealsFound(Integer dealsFound) {
        this.dealsFound = dealsFound;
    }
}
