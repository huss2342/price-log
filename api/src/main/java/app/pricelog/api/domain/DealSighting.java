package app.pricelog.api.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/** A promotion published by a retailer, read from their own public listing. */
@Entity
@Table(name = "deal_sighting")
public class DealSighting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Chain chain;

    /** Chain, item number and title, so a re-read updates rather than duplicates. */
    @Column(nullable = false, unique = true, length = 255)
    private String fingerprint;

    @Column(name = "item_number", length = 32)
    private String itemNumber;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "discount_cents")
    private Integer discountCents;

    /** False for online-only offers, which are useless when standing in a warehouse. */
    @Column(name = "in_warehouse", nullable = false)
    private boolean inWarehouse = true;

    @Column(name = "first_seen_on", nullable = false)
    private LocalDate firstSeenOn;

    @Column(name = "last_seen_on", nullable = false)
    private LocalDate lastSeenOn;

    /** Cleared when a refresh no longer finds the offer. */
    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public Chain getChain() {
        return chain;
    }

    public void setChain(Chain chain) {
        this.chain = chain;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getItemNumber() {
        return itemNumber;
    }

    public void setItemNumber(String itemNumber) {
        this.itemNumber = itemNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getDiscountCents() {
        return discountCents;
    }

    public void setDiscountCents(Integer discountCents) {
        this.discountCents = discountCents;
    }

    public boolean isInWarehouse() {
        return inWarehouse;
    }

    public void setInWarehouse(boolean inWarehouse) {
        this.inWarehouse = inWarehouse;
    }

    public LocalDate getFirstSeenOn() {
        return firstSeenOn;
    }

    public void setFirstSeenOn(LocalDate firstSeenOn) {
        this.firstSeenOn = firstSeenOn;
    }

    public LocalDate getLastSeenOn() {
        return lastSeenOn;
    }

    public void setLastSeenOn(LocalDate lastSeenOn) {
        this.lastSeenOn = lastSeenOn;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
