package app.pricelog.api.web;

import app.pricelog.api.domain.*;
import app.pricelog.api.extract.ProductResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** One logged price, flattened for the UI. */
public record ObservationView(
        Long id,
        Long productId,
        String productName,
        String brand,
        Category category,
        /** Products sharing this are compared with each other. */
        String comparisonKey,
        /** What the item is, without brand or size: "chicken sausage". */
        String commodity,
        Set<QualityAttribute> attributes,
        BigDecimal sizeValue,
        String sizeUnit,
        Integer packCount,
        BaseUnit baseUnit,
        BigDecimal baseQuantity,
        Long storeId,
        Chain chain,
        String storeLabel,
        LocalDate observedOn,
        int priceCents,
        Integer regularPriceCents,
        boolean onSale,
        SaleSignal saleSignal,
        LocalDate saleEndsOn,
        boolean discontinued,
        BigDecimal unitPriceCents,
        String itemNumber,
        String photoUrl,
        BigDecimal confidence,
        boolean needsReview,
        String notes,
        /** Plain-language reading of the tag conventions that matched. */
        List<String> tagInsights,
        String advice,

        // How this price compares to the past. Shown on every capture, because
        // "is this cheaper than last time" is the question you are actually
        // asking while standing in front of the shelf.
        boolean watched,
        Integer targetPriceCents,
        Integer previousPriceCents,
        LocalDate previousObservedOn,
        /** Negative means it got cheaper since the last sighting. */
        Integer changeCents,
        BigDecimal changePercent,
        Integer lowestBeforeCents,
        /** True when this is the cheapest this item has ever been recorded. */
        boolean lowestEver,
        boolean meetsTarget) {

    /**
     * This user's watch state for the product: whether they watch it and the
     * target price they set. Watch data lives per user, so it is resolved in
     * the service layer and passed in rather than read off the shared product.
     */
    public record WatchState(boolean watched, Integer targetPriceCents) {
        public static final WatchState NONE = new WatchState(false, null);
    }

    public static ObservationView of(PriceObservation o, WatchState watch) {
        Product p = o.getProduct();
        Store s = o.getStore();

        Integer previous = o.getPreviousPriceCents();
        Integer change = previous == null ? null : o.getPriceCents() - previous;
        BigDecimal changePercent = null;
        if (previous != null && previous > 0) {
            changePercent = BigDecimal.valueOf(change)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
        }

        Integer lowestBefore = o.getLowestBeforeCents();
        boolean lowestEver = lowestBefore != null && o.getPriceCents() < lowestBefore;

        Integer target = watch.targetPriceCents();
        boolean meetsTarget = target != null && o.getPriceCents() <= target;

        return new ObservationView(
                o.getId(), p.getId(), p.getDisplayName(), p.getBrand(), p.getCategory(),
                p.getComparisonKey(), ProductResolver.commodityOf(p), p.getAttributes(), p.getSizeValue(), p.getSizeUnit(), p.getPackCount(),
                p.getBaseUnit(), p.getBaseQuantity(),
                s.getId(), s.getChain(), s.getLabel(),
                o.getObservedOn(), o.getPriceCents(), o.getRegularPriceCents(), o.isOnSale(),
                o.getSaleSignal(), o.getSaleEndsOn(), o.isDiscontinued(), o.getUnitPriceCents(),
                o.getItemNumber(), o.getPhotoUrl(), o.getConfidence(), o.isNeedsReview(),
                o.getNotes(), o.getTagInsights(), o.getAdvice(),
                watch.watched(), target, previous, o.getPreviousObservedOn(),
                change, changePercent, lowestBefore, lowestEver, meetsTarget);
    }
}
