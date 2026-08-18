package app.pricelog.api.extract;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.SaleSignal;
import app.pricelog.api.domain.TagRule;
import app.pricelog.api.repo.TagRuleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a store's price tag conventions to an extracted tag. This is the part
 * that turns "$8.97" into "store clearance, may drop again before it sells out".
 */
@Service
public class TagRuleEngine {

    private final TagRuleRepository rules;

    public TagRuleEngine(TagRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public TagVerdict evaluate(Chain chain, int priceCents, List<String> markers, String rawText) {
        String haystack = rawText == null ? "" : rawText.toUpperCase(Locale.ROOT);
        List<String> upperMarkers = markers.stream().map(m -> m.toUpperCase(Locale.ROOT)).toList();

        List<TagRule> matched = new ArrayList<>();
        for (TagRule rule : rules.findByChainAndEnabledTrueOrderByPriorityAsc(chain)) {
            if (applies(rule, priceCents, upperMarkers, haystack)) {
                matched.add(rule);
            }
        }

        if (matched.isEmpty()) {
            return TagVerdict.unknown();
        }

        TagRule primary = matched.getFirst();
        boolean discontinued = matched.stream().anyMatch(r -> r.getSignal() == SaleSignal.DISCONTINUED);
        // Any matched rule can mean a price cut, not only the top-priority one:
        // a discontinued item marked down to .97 is both gone soon and cheaper.
        boolean discounted = matched.stream().anyMatch(r -> isDiscount(r.getSignal()));
        List<String> descriptions = matched.stream().map(TagRule::getMeaning).toList();

        return new TagVerdict(primary.getSignal(), primary.getMeaning(), primary.getAdvice(),
                discontinued, discounted, descriptions);
    }

    private boolean isDiscount(SaleSignal signal) {
        return switch (signal) {
            case INSTANT_SAVINGS, CLEARANCE, MANAGER_MARKDOWN -> true;
            case REGULAR, DISCONTINUED, UNKNOWN -> false;
        };
    }

    private boolean applies(TagRule rule, int priceCents, List<String> markers, String haystack) {
        return switch (rule.getMatchType()) {
            case PRICE_ENDING -> matchesPriceEnding(rule.getPattern(), priceCents);
            case MARKER -> markers.contains(rule.getPattern().toUpperCase(Locale.ROOT));
            case TEXT_CONTAINS -> !haystack.isEmpty()
                    && haystack.contains(rule.getPattern().toUpperCase(Locale.ROOT));
        };
    }

    /** Pattern is the trailing cents, written as ".97" or "97". */
    private boolean matchesPriceEnding(String pattern, int priceCents) {
        String digits = pattern.replaceAll("[^0-9]", "");
        if (digits.length() != 2) {
            return false;
        }
        return Math.floorMod(priceCents, 100) == Integer.parseInt(digits);
    }
}
