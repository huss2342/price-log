package app.pricelog.api.extract;

import app.pricelog.api.domain.*;
import app.pricelog.api.repo.ProductRepository;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an extracted tag into the {@link Product} row it belongs to, creating
 * one on first sight. Two keys are maintained deliberately:
 * the normalized key collapses the same SKU seen at different stores, and the
 * comparison key collapses substitutable products across brands.
 */
@Service
public class ProductResolver {

    private final ProductRepository products;
    private final UnitNormalizer normalizer;

    public ProductResolver(ProductRepository products, UnitNormalizer normalizer) {
        this.products = products;
        this.normalizer = normalizer;
    }

    @Transactional
    public Product resolve(ExtractedTag tag) {
        Category category = Enums.parseOr(Category.class, tag.category(), Category.OTHER);
        Set<QualityAttribute> attributes = parseAttributes(tag);
        UnitNormalizer.Normalized size =
                normalizer.normalize(tag.sizeValue(), tag.sizeUnit(), tag.packCount());

        String displayName = firstNonBlank(tag.itemName(), tag.commodity(), "Unnamed item");
        String normalizedKey = buildNormalizedKey(tag, displayName, size);
        String comparisonKey = buildComparisonKey(tag, category, attributes);

        return products.findByNormalizedKey(normalizedKey).orElseGet(() -> {
            Product product = new Product();
            product.setNormalizedKey(normalizedKey);
            product.setComparisonKey(comparisonKey);
            product.setDisplayName(displayName);
            product.setBrand(blankToNull(tag.brand()));
            product.setCategory(category);
            product.setSizeValue(tag.sizeValue());
            product.setSizeUnit(blankToNull(tag.sizeUnit()));
            product.setPackCount(tag.packCount());
            product.setBaseUnit(size.unit());
            product.setBaseQuantity(size.quantity());
            product.setAttributes(attributes);
            return products.save(product);
        });
    }

    /** Recompute the derived fields after a manual correction on the review screen. */
    @Transactional
    public Product recompute(Product product) {
        UnitNormalizer.Normalized size = normalizer.normalize(
                product.getSizeValue(), product.getSizeUnit(), product.getPackCount());
        product.setBaseUnit(size.unit());
        product.setBaseQuantity(size.quantity());
        product.setComparisonKey(comparisonKey(
                product.getDisplayName(), product.getCategory(), product.getAttributes()));
        return products.save(product);
    }

    private Set<QualityAttribute> parseAttributes(ExtractedTag tag) {
        Set<QualityAttribute> result = EnumSet.noneOf(QualityAttribute.class);
        for (String raw : tag.attributesOrEmpty()) {
            Enums.parse(QualityAttribute.class, raw).ifPresent(result::add);
        }
        return result;
    }

    /**
     * Brand, name and total size. Deliberately includes size: a 24 ct and a
     * 12 ct of the same eggs are different purchases, even if comparable.
     */
    private String buildNormalizedKey(ExtractedTag tag, String displayName,
                                      UnitNormalizer.Normalized size) {
        String sizePart = size.unit() == BaseUnit.NONE || size.quantity() == null
                ? "nosize"
                : size.quantity().stripTrailingZeros().toPlainString() + size.unit().name();
        return String.join("|",
                slug(firstNonBlank(tag.brand(), "nobrand")),
                slug(displayName),
                sizePart);
    }

    private String buildComparisonKey(ExtractedTag tag, Category category,
                                      Set<QualityAttribute> attributes) {
        String basis = firstNonBlank(tag.commodity(), tag.itemName(), category.name());
        return comparisonKey(basis, category, attributes);
    }

    /**
     * Category, commodity, and quality claims, but never brand or size. This is
     * what "where are organic eggs cheapest" groups on, so conventional eggs
     * must never land in the same bucket as pasture-raised ones.
     */
    private String comparisonKey(String basis, Category category, Set<QualityAttribute> attributes) {
        String attributePart = attributes.isEmpty()
                ? "plain"
                : attributes.stream()
                        .map(Enum::name)
                        .collect(Collectors.toCollection(TreeSet::new))
                        .stream()
                        .collect(Collectors.joining("+"));
        return String.join("|", category.name(), slug(basis), attributePart);
    }

    private String slug(String raw) {
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Exposed so the capture service can reuse the same unit maths. */
    public BigDecimal unitPriceCents(int priceCents, Product product) {
        if (product.getBaseUnit() == null || product.getBaseUnit() == BaseUnit.NONE
                || product.getBaseQuantity() == null) {
            return null;
        }
        return normalizer.unitPriceCents(priceCents,
                new UnitNormalizer.Normalized(product.getBaseUnit(), product.getBaseQuantity()));
    }
}
