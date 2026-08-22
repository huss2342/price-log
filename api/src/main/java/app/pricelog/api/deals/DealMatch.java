package app.pricelog.api.deals;

import java.time.LocalDate;

/**
 * A published promotion matched to something already logged, joined on the
 * retailer's item number so the match is exact rather than a name guess.
 *
 * @param lastPriceCents what was paid the last time this item was photographed
 * @param impliedPriceCents what it should ring up at: the published sale price
 *                          when Costco states one, otherwise the last price
 *                          seen minus the published discount, which is only an
 *                          estimate because that price may itself be stale
 */
public record DealMatch(
        Long productId,
        String productName,
        String brand,
        String itemNumber,
        String dealTitle,
        Integer salePriceCents,
        Integer discountCents,
        boolean inWarehouse,
        boolean watched,
        Integer lastPriceCents,
        Integer impliedPriceCents,
        LocalDate lastSeenOn,
        long daysSinceSeen) {
}
