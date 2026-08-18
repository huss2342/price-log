package app.pricelog.api.web;

import app.pricelog.api.domain.Category;
import app.pricelog.api.domain.QualityAttribute;
import app.pricelog.api.domain.SaleSignal;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Corrections from the review screen. Every field is optional; only what you
 * send is changed. Product-level fields update the shared product row, so a
 * correction fixes past and future observations of the same item at once.
 */
public record ObservationUpdate(
        String productName,
        String brand,
        Category category,
        Set<QualityAttribute> attributes,
        BigDecimal sizeValue,
        String sizeUnit,
        Integer packCount,
        Long storeId,
        LocalDate observedOn,
        @PositiveOrZero Integer priceCents,
        @PositiveOrZero Integer regularPriceCents,
        Boolean onSale,
        SaleSignal saleSignal,
        LocalDate saleEndsOn,
        Boolean discontinued,
        String itemNumber,
        String notes,
        /** Send true to clear the review flag once the row looks right. */
        Boolean reviewed) {
}
