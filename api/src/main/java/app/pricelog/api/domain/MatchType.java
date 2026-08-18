package app.pricelog.api.domain;

/** How a {@link TagRule} decides whether it applies to an extracted tag. */
public enum MatchType {
    /** Compares the trailing cents of the price, e.g. ".97". */
    PRICE_ENDING,
    /** A visual marker the vision model reports, e.g. "ASTERISK". */
    MARKER,
    /** Case-insensitive substring of the tag's raw text. */
    TEXT_CONTAINS
}
