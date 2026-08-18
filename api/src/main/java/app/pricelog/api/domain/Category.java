package app.pricelog.api.domain;

/**
 * Coarse buckets for browsing. Deliberately shallow: the comparison key does
 * the precise grouping, this only drives the category tiles in the UI.
 */
public enum Category {
    EGGS,
    DAIRY,
    MEAT,
    SEAFOOD,
    PRODUCE,
    BAKERY,
    PANTRY,
    FROZEN,
    BEVERAGES,
    SNACKS,
    HOUSEHOLD,
    PAPER_GOODS,
    PERSONAL_CARE,
    SUPPLEMENTS,
    PET,
    ELECTRONICS,
    APPAREL,
    HOME,
    OTHER
}
