package app.pricelog.api.query;

import app.pricelog.api.domain.BaseUnit;
import app.pricelog.api.domain.Category;
import app.pricelog.api.domain.QualityAttribute;
import java.util.List;
import java.util.Set;

/**
 * One set of substitutable products, with every store's latest price for them.
 * This is the answer to "where are organic eggs cheapest".
 */
public record CompareGroup(
        String comparisonKey,
        String label,
        Category category,
        Set<QualityAttribute> attributes,
        /** Unit the offers are compared in; NONE means only absolute prices are comparable. */
        BaseUnit baseUnit,
        /** Cheapest first. */
        List<StoreOffer> offers) {

    public StoreOffer best() {
        return offers.isEmpty() ? null : offers.getFirst();
    }
}
