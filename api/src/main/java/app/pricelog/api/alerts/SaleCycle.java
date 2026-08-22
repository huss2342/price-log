package app.pricelog.api.alerts;

import java.time.LocalDate;

/**
 * How often an item has been seen discounted, and whether another discount
 * looks overdue.
 *
 * @param salesSeen        how many discounted sightings the estimate rests on
 * @param averageGapDays   mean days between the starts of those sales
 * @param lastSaleOn       the most recent discounted sighting
 * @param daysSinceLastSale days elapsed since it
 * @param dueInDays        negative when the next sale already looks overdue
 * @param confident        false when there are too few sales to mean much
 */
public record SaleCycle(
        int salesSeen,
        Integer averageGapDays,
        LocalDate lastSaleOn,
        Long daysSinceLastSale,
        Integer dueInDays,
        boolean confident) {

    public static SaleCycle unknown() {
        return new SaleCycle(0, null, null, null, null, false);
    }
}
