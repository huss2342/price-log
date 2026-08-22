package app.pricelog.api.deals;

import java.time.LocalDate;

/**
 * A published promotion matched to something already logged, joined on the
 * retailer's item number so the match is exact rather than a name guess.
 *
 * @param lastPriceCents what was paid the last time this item was photographed
 * @param impliedPriceCents last price minus the published discount, when both
 *                          are known — an estimate, since the last price may
 *                          itself be stale
 */
public record DealMatch(
        Long productId,
        String productName,
        String brand,
        String itemNumber,
        String dealTitle,
        Integer discountCents,
        boolean inWarehouse,
        boolean watched,
        Integer lastPriceCents,
        Integer impliedPriceCents,
        LocalDate lastSeenOn,
        long daysSinceSeen) {
}
