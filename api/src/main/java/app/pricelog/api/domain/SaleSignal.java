package app.pricelog.api.domain;

/**
 * What the tag's pricing pattern means. Ordered loosely from "will come back"
 * to "gone for good", which is the axis that decides buy-now versus wait.
 */
public enum SaleSignal {
    /** Everyday price, nothing discounted. */
    REGULAR,
    /** Scheduled manufacturer promotion. Recurs, so waiting is viable. */
    INSTANT_SAVINGS,
    /** Store-level markdown. Real, but may drop further. */
    CLEARANCE,
    /** Deepest store markdown, typically the floor. */
    MANAGER_MARKDOWN,
    /** Not being restocked. Waiting means losing the item entirely. */
    DISCONTINUED,
    /** Tag read, but no pattern matched. */
    UNKNOWN
}
