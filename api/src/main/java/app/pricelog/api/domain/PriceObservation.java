package app.pricelog.api.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One price tag, seen at one store, on one day. */
@Entity
@Table(name = "price_observation")
public class PriceObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "observed_on", nullable = false)
    private LocalDate observedOn;

    /** What you would pay at the register today. */
    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    /** Pre-discount price when the tag shows one. */
    @Column(name = "regular_price_cents")
    private Integer regularPriceCents;

    @Column(name = "on_sale", nullable = false)
    private boolean onSale;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_signal", length = 32)
    private SaleSignal saleSignal = SaleSignal.UNKNOWN;

    @Column(name = "sale_ends_on")
    private LocalDate saleEndsOn;

    /** Tag indicates the item is not being restocked. */
    @Column(nullable = false)
    private boolean discontinued;

    /** priceCents divided by the product's baseQuantity. */
    @Column(name = "unit_price_cents", precision = 14, scale = 4)
    private BigDecimal unitPriceCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit", length = 16)
    private BaseUnit baseUnit;

    @Column(name = "item_number", length = 32)
    private String itemNumber;

    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    /** Cleared once you confirm the extraction on the review screen. */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview = true;

    // LONGVARCHAR maps to TEXT on PostgreSQL and CLOB on H2. Avoids @Lob, which
    // would make PostgreSQL treat this as a large object OID rather than text.
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "raw_extraction")
    private String rawExtraction;

    /** One matched rule's meaning per line, in priority order. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "tag_insights")
    private String tagInsights;

    @Column(length = 255)
    private String advice;

    @Column(length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public LocalDate getObservedOn() {
        return observedOn;
    }

    public void setObservedOn(LocalDate observedOn) {
        this.observedOn = observedOn;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(int priceCents) {
        this.priceCents = priceCents;
    }

    public Integer getRegularPriceCents() {
        return regularPriceCents;
    }

    public void setRegularPriceCents(Integer regularPriceCents) {
        this.regularPriceCents = regularPriceCents;
    }

    public boolean isOnSale() {
        return onSale;
    }

    public void setOnSale(boolean onSale) {
        this.onSale = onSale;
    }

    public SaleSignal getSaleSignal() {
        return saleSignal;
    }

    public void setSaleSignal(SaleSignal saleSignal) {
        this.saleSignal = saleSignal;
    }

    public LocalDate getSaleEndsOn() {
        return saleEndsOn;
    }

    public void setSaleEndsOn(LocalDate saleEndsOn) {
        this.saleEndsOn = saleEndsOn;
    }

    public boolean isDiscontinued() {
        return discontinued;
    }

    public void setDiscontinued(boolean discontinued) {
        this.discontinued = discontinued;
    }

    public BigDecimal getUnitPriceCents() {
        return unitPriceCents;
    }

    public void setUnitPriceCents(BigDecimal unitPriceCents) {
        this.unitPriceCents = unitPriceCents;
    }

    public BaseUnit getBaseUnit() {
        return baseUnit;
    }

    public void setBaseUnit(BaseUnit baseUnit) {
        this.baseUnit = baseUnit;
    }

    public String getItemNumber() {
        return itemNumber;
    }

    public void setItemNumber(String itemNumber) {
        this.itemNumber = itemNumber;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    public String getRawExtraction() {
        return rawExtraction;
    }

    public void setRawExtraction(String rawExtraction) {
        this.rawExtraction = rawExtraction;
    }

    /** @return the matched rule meanings, one per line; never null */
    public List<String> getTagInsights() {
        if (tagInsights == null || tagInsights.isBlank()) {
            return List.of();
        }
        return List.of(tagInsights.split("\n"));
    }

    public void setTagInsights(List<String> insights) {
        this.tagInsights = insights == null || insights.isEmpty() ? null : String.join("\n", insights);
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
