package app.pricelog.api.domain;

/**
 * The canonical unit a product's size is reduced to. Everything comparable
 * must share one of these, so a 5 lb bag and a 32 oz bag both land on OZ.
 */
public enum BaseUnit {
    /** Weight. */
    OZ,
    /** Volume. */
    FL_OZ,
    /** Countable goods: eggs, rolls, capsules, cans in a pack. */
    COUNT,
    /** Size could not be determined; only the absolute price is comparable. */
    NONE
}
