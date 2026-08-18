package app.pricelog.api.domain;

/**
 * Quality claims printed on the tag. These participate in the comparison key,
 * so organic eggs are never priced against conventional ones by accident.
 */
public enum QualityAttribute {
    ORGANIC,
    PASTURE_RAISED,
    FREE_RANGE,
    CAGE_FREE,
    GRASS_FED,
    GRASS_FINISHED,
    WILD_CAUGHT,
    FARM_RAISED,
    NON_GMO,
    ANTIBIOTIC_FREE,
    HORMONE_FREE,
    A2_MILK,
    RAW,
    KOSHER,
    HALAL,
    GLUTEN_FREE,
    PRIME_GRADE,
    CHOICE_GRADE
}
