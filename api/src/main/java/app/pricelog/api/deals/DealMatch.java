package app.pricelog.api.deals;

import java.time.LocalDate;

/**
 * A published promotion matched to something already logged, joined on the
 * retailer's item number so the match is exact rather than a name guess.
 *
 * <p>Stated as before and after, because that is how a deal is read: it was
 * $13.99, it is $9.99 until the 20th. Costco usually prints only one of the two
 * figures, so the other comes from the log.
 *
 * @param regularPriceCents what the item costs when it is not on sale, from the
 *                          newest sighting that showed it; null if never seen
 * @param dealPriceCents    what it rings up at during the promotion
 * @param discountCents     how much the promotion takes off
 * @param estimated         true when either figure was worked out from the log
 *                          rather than printed in the listing
 * @param validUntil        last day of the promotion, when the listing says
 * @param lastSeenOn        when the item was last photographed
 */
public record DealMatch(
        Long productId,
        String productName,
        String brand,
        String itemNumber,
        String dealTitle,
        Integer regularPriceCents,
        Integer dealPriceCents,
        Integer discountCents,
        boolean estimated,
        boolean inWarehouse,
        LocalDate validUntil,
        LocalDate lastSeenOn,
        long daysSinceSeen) {
}
