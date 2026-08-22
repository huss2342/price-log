package app.pricelog.api.alerts;

import java.time.LocalDate;

/** A watched item, with its sale rhythm and where it currently stands. */
public record WatchedItem(
        Long productId,
        String productName,
        String brand,
        String itemNumber,
        Integer targetPriceCents,
        Integer lastPriceCents,
        Integer bestPriceCents,
        LocalDate lastSeenOn,
        long daysSinceSeen,
        boolean meetsTarget,
        SaleCycle cycle,
        /** True when a sale looks overdue by the item's own rhythm. */
        boolean dueForSale) {
}
