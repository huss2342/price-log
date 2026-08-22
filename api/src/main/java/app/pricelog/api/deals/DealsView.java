package app.pricelog.api.deals;

import java.time.Instant;
import java.util.List;

/**
 * @param matches      promotions on items already in the log, watched first
 * @param totalDeals   how many promotions the source listed, matched or not
 * @param lastCheckedAt when the source was last read successfully
 * @param error        why the last read failed, when it did; the matches are
 *                     then whatever was cached from the previous success
 */
public record DealsView(
        List<DealMatch> matches,
        int totalDeals,
        Instant lastCheckedAt,
        String error) {
}
