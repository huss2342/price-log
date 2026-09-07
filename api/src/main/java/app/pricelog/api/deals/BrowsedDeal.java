package app.pricelog.api.deals;

import java.time.LocalDate;

/**
 * One active promotion as published, for browsing the whole listing rather than
 * only the handful that matched something logged.
 *
 * @param logged true when this item number has been photographed at least once,
 *               so the deals screen has already had something to say about it
 */
public record BrowsedDeal(
        String itemNumber,
        String title,
        Integer salePriceCents,
        Integer discountCents,
        boolean inWarehouse,
        boolean logged,
        LocalDate firstSeenOn,
        LocalDate lastSeenOn) {
}
