package app.pricelog.api.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Brand + name + size. Uniquely identifies one SKU across all stores. */
    @Column(name = "normalized_key", nullable = false, unique = true, length = 255)
    private String normalizedKey;

    /** Category + quality attributes, no brand or size. Groups price comparisons. */
    @Column(name = "comparison_key", nullable = false, length = 255)
    private String comparisonKey;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(length = 128)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private Category category;

    /** Size as printed, e.g. 5 for "5 lb". Null when the tag shows no size. */
    @Column(name = "size_value", precision = 14, scale = 4)
    private BigDecimal sizeValue;

    /** Unit as printed, e.g. "lb", "fl oz", "ct". */
    @Column(name = "size_unit", length = 16)
    private String sizeUnit;

    /** Items per pack for multipacks, e.g. 2 for "2 x 32 oz". */
    @Column(name = "pack_count")
    private Integer packCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit", length = 16)
    private BaseUnit baseUnit = BaseUnit.NONE;

    /** Total size in {@link #baseUnit}: packCount * sizeValue, converted. */
    @Column(name = "base_quantity", precision = 16, scale = 4)
    private BigDecimal baseQuantity;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_attribute", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "attribute", length = 48)
    @Enumerated(EnumType.STRING)
    private Set<QualityAttribute> attributes = EnumSet.noneOf(QualityAttribute.class);

    /** Flagged for the alerts screen. */
    @Column(nullable = false)
    private boolean watched = false;

    /** Optional "tell me when it reaches this", in cents. */
    @Column(name = "target_price_cents")
    private Integer targetPriceCents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Product() {
    }

    public Long getId() {
        return id;
    }

    public String getNormalizedKey() {
        return normalizedKey;
    }

    public void setNormalizedKey(String normalizedKey) {
        this.normalizedKey = normalizedKey;
    }

    public String getComparisonKey() {
        return comparisonKey;
    }

    public void setComparisonKey(String comparisonKey) {
        this.comparisonKey = comparisonKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public BigDecimal getSizeValue() {
        return sizeValue;
    }

    public void setSizeValue(BigDecimal sizeValue) {
        this.sizeValue = sizeValue;
    }

    public String getSizeUnit() {
        return sizeUnit;
    }

    public void setSizeUnit(String sizeUnit) {
        this.sizeUnit = sizeUnit;
    }

    public Integer getPackCount() {
        return packCount;
    }

    public void setPackCount(Integer packCount) {
        this.packCount = packCount;
    }

    public BaseUnit getBaseUnit() {
        return baseUnit;
    }

    public void setBaseUnit(BaseUnit baseUnit) {
        this.baseUnit = baseUnit;
    }

    public BigDecimal getBaseQuantity() {
        return baseQuantity;
    }

    public void setBaseQuantity(BigDecimal baseQuantity) {
        this.baseQuantity = baseQuantity;
    }

    public Set<QualityAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(Set<QualityAttribute> attributes) {
        this.attributes = attributes;
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
