package app.pricelog.api.extract;

import app.pricelog.api.domain.SaleSignal;
import java.util.List;

/**
 * What the tag's pricing conventions imply, after store rules are applied.
 *
 * @param signal       the highest-priority signal that matched
 * @param meaning      plain-language reading of that signal
 * @param advice       whether to buy now or wait, and why
 * @param discontinued true when any matched rule says the item is not restocking
 * @param discounted   true when any matched rule means the price is reduced.
 *                     Tracked separately from {@code signal} because the
 *                     highest-priority signal is often DISCONTINUED, which says
 *                     nothing about whether the price was also cut.
 * @param matched      every rule that fired, most significant first
 */
public record TagVerdict(
        SaleSignal signal,
        String meaning,
        String advice,
        boolean discontinued,
        boolean discounted,
        List<String> matched) {

    public static TagVerdict unknown() {
        return new TagVerdict(SaleSignal.UNKNOWN, "No known tag convention matched this price.",
                null, false, false, List.of());
    }
}
