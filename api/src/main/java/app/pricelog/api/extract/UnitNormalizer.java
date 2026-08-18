package app.pricelog.api.extract;

import app.pricelog.api.domain.BaseUnit;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reduces printed sizes to one canonical unit per measurement family, so a
 * 5 lb bag and a 32 oz bag can be priced against each other.
 */
@Component
public class UnitNormalizer {

    /** Multipliers into ounces. */
    private static final Map<String, BigDecimal> WEIGHT = Map.ofEntries(
            Map.entry("oz", BigDecimal.ONE),
            Map.entry("ounce", BigDecimal.ONE),
            Map.entry("ounces", BigDecimal.ONE),
            Map.entry("lb", new BigDecimal("16")),
            Map.entry("lbs", new BigDecimal("16")),
            Map.entry("pound", new BigDecimal("16")),
            Map.entry("pounds", new BigDecimal("16")),
            Map.entry("g", new BigDecimal("0.03527396")),
            Map.entry("gram", new BigDecimal("0.03527396")),
            Map.entry("grams", new BigDecimal("0.03527396")),
            Map.entry("kg", new BigDecimal("35.27396")),
            Map.entry("kilogram", new BigDecimal("35.27396")),
            Map.entry("kilograms", new BigDecimal("35.27396")));

    /** Multipliers into fluid ounces. */
    private static final Map<String, BigDecimal> VOLUME = Map.ofEntries(
            Map.entry("floz", BigDecimal.ONE),
            Map.entry("fluidounce", BigDecimal.ONE),
            Map.entry("fluidounces", BigDecimal.ONE),
            Map.entry("ml", new BigDecimal("0.03381402")),
            Map.entry("milliliter", new BigDecimal("0.03381402")),
            Map.entry("milliliters", new BigDecimal("0.03381402")),
            Map.entry("l", new BigDecimal("33.81402")),
            Map.entry("liter", new BigDecimal("33.81402")),
            Map.entry("liters", new BigDecimal("33.81402")),
            Map.entry("litre", new BigDecimal("33.81402")),
            Map.entry("gal", new BigDecimal("128")),
            Map.entry("gallon", new BigDecimal("128")),
            Map.entry("gallons", new BigDecimal("128")),
            Map.entry("qt", new BigDecimal("32")),
            Map.entry("quart", new BigDecimal("32")),
            Map.entry("pt", new BigDecimal("16")),
            Map.entry("pint", new BigDecimal("16")),
            Map.entry("cup", new BigDecimal("8")),
            Map.entry("cups", new BigDecimal("8")));

    /** Units that mean "one of a thing" and normalize to COUNT at 1:1. */
    private static final java.util.Set<String> COUNTABLE = java.util.Set.of(
            "ct", "count", "counts", "pk", "pack", "packs", "ea", "each", "unit", "units",
            "roll", "rolls", "sheet", "sheets", "capsule", "capsules", "tablet", "tablets",
            "softgel", "softgels", "gummy", "gummies", "bar", "bars", "can", "cans",
            "bottle", "bottles", "pouch", "pouches", "bag", "bags", "box", "boxes",
            "serving", "servings", "piece", "pieces", "slice", "slices", "egg", "eggs",
            "wipe", "wipes", "load", "loads", "dose", "doses", "pods", "pod");

    /** Total size in a canonical unit, plus which unit that is. */
    public record Normalized(BaseUnit unit, BigDecimal quantity) {

        public static Normalized none() {
            return new Normalized(BaseUnit.NONE, null);
        }
    }

    /**
     * @param sizeValue size of a single unit as printed
     * @param sizeUnit  unit as printed, e.g. "fl oz"
     * @param packCount identical units in the package, null treated as 1
     */
    public Normalized normalize(BigDecimal sizeValue, String sizeUnit, Integer packCount) {
        if (sizeUnit == null || sizeUnit.isBlank()) {
            return Normalized.none();
        }

        String key = canonicalize(sizeUnit);
        int packs = packCount == null || packCount < 1 ? 1 : packCount;

        BigDecimal effectiveSize = sizeValue;
        // "30 rolls" often arrives as packCount 30 with no size, because one roll
        // has no size of its own. A countable unit with a pack count is still a
        // quantity, so treat each unit as one.
        if (effectiveSize == null && COUNTABLE.contains(key) && packCount != null && packCount >= 1) {
            effectiveSize = BigDecimal.ONE;
        }
        if (effectiveSize == null || effectiveSize.signum() <= 0) {
            return Normalized.none();
        }

        BigDecimal total = effectiveSize.multiply(BigDecimal.valueOf(packs));

        BigDecimal weightFactor = WEIGHT.get(key);
        if (weightFactor != null) {
            return new Normalized(BaseUnit.OZ, scale(total.multiply(weightFactor)));
        }

        BigDecimal volumeFactor = VOLUME.get(key);
        if (volumeFactor != null) {
            return new Normalized(BaseUnit.FL_OZ, scale(total.multiply(volumeFactor)));
        }

        if (COUNTABLE.contains(key)) {
            return new Normalized(BaseUnit.COUNT, scale(total));
        }

        return Normalized.none();
    }

    /** Price per base unit, in cents, or null when the size is unknown. */
    public BigDecimal unitPriceCents(int priceCents, Normalized normalized) {
        if (normalized.unit() == BaseUnit.NONE
                || normalized.quantity() == null
                || normalized.quantity().signum() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(priceCents)
                .divide(normalized.quantity(), new MathContext(10, RoundingMode.HALF_UP))
                .setScale(4, RoundingMode.HALF_UP);
    }

    /** Lowercase and strip everything that varies between tags: spaces, dots, periods. */
    private String canonicalize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
