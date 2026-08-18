package app.pricelog.api.web;

import app.pricelog.api.domain.*;
import java.math.BigDecimal;
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
        String advice) {

    public static ObservationView of(PriceObservation o) {
        Product p = o.getProduct();
        Store s = o.getStore();
        return new ObservationView(
                o.getId(), p.getId(), p.getDisplayName(), p.getBrand(), p.getCategory(),
                p.getAttributes(), p.getSizeValue(), p.getSizeUnit(), p.getPackCount(),
                p.getBaseUnit(), p.getBaseQuantity(),
                s.getId(), s.getChain(), s.getLabel(),
                o.getObservedOn(), o.getPriceCents(), o.getRegularPriceCents(), o.isOnSale(),
                o.getSaleSignal(), o.getSaleEndsOn(), o.isDiscontinued(), o.getUnitPriceCents(),
                o.getItemNumber(), o.getPhotoUrl(), o.getConfidence(), o.isNeedsReview(),
                o.getNotes(), o.getTagInsights(), o.getAdvice());
    }
}
