package app.pricelog.api.query;

import app.pricelog.api.domain.BaseUnit;
import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.SaleSignal;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The most recent price for one product at one store, positioned against the
 * cheapest option in its comparison group.
 */
public record StoreOffer(
        Long observationId,
        Long storeId,
        Chain chain,
        String storeLabel,
        Long productId,
        String productName,
        String brand,
        int priceCents,
        Integer regularPriceCents,
        boolean onSale,
        SaleSignal saleSignal,
        boolean discontinued,
        BigDecimal unitPriceCents,
        BaseUnit baseUnit,
        LocalDate observedOn,
        long daysAgo,
        /** Older than the staleness window, so treat the price as a hint not a fact. */
        boolean stale,
        /** How much more this costs per unit than the cheapest offer, as a percentage. */
        BigDecimal percentAboveBest) {
}
