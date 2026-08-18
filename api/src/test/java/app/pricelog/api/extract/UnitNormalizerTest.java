package app.pricelog.api.extract;

import static org.assertj.core.api.Assertions.assertThat;

import app.pricelog.api.domain.BaseUnit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UnitNormalizerTest {

    private final UnitNormalizer normalizer = new UnitNormalizer();

    @Test
    void poundsBecomeOunces() {
        var result = normalizer.normalize(new BigDecimal("5"), "lb", 1);

        assertThat(result.unit()).isEqualTo(BaseUnit.OZ);
        assertThat(result.quantity()).isEqualByComparingTo("80");
    }

    @Test
    void packCountMultipliesTheSize() {
        // "2 x 32 oz" is 64 oz of product, not 32.
        var result = normalizer.normalize(new BigDecimal("32"), "oz", 2);

        assertThat(result.unit()).isEqualTo(BaseUnit.OZ);
        assertThat(result.quantity()).isEqualByComparingTo("64");
    }

    @Test
    void fluidOuncesStayVolumeAndDoNotMixWithWeight() {
        var volume = normalizer.normalize(new BigDecimal("16.9"), "fl oz", 24);
        var weight = normalizer.normalize(new BigDecimal("16.9"), "oz", 24);

        assertThat(volume.unit()).isEqualTo(BaseUnit.FL_OZ);
        assertThat(weight.unit()).isEqualTo(BaseUnit.OZ);
    }

    @Test
    void litersConvertToFluidOunces() {
        var result = normalizer.normalize(new BigDecimal("2"), "L", 1);

        assertThat(result.unit()).isEqualTo(BaseUnit.FL_OZ);
        assertThat(result.quantity()).isEqualByComparingTo("67.6280");
    }

    @Test
    void countableUnitsNormalizeOneToOne() {
        var result = normalizer.normalize(new BigDecimal("24"), "ct", 1);

        assertThat(result.unit()).isEqualTo(BaseUnit.COUNT);
        assertThat(result.quantity()).isEqualByComparingTo("24");
    }

    @Test
    void unitSpellingAndPunctuationDoNotMatter() {
        assertThat(normalizer.normalize(BigDecimal.ONE, "FL. OZ.", 1).unit()).isEqualTo(BaseUnit.FL_OZ);
        assertThat(normalizer.normalize(BigDecimal.ONE, "Pounds", 1).unit()).isEqualTo(BaseUnit.OZ);
        assertThat(normalizer.normalize(BigDecimal.ONE, "count", 1).unit()).isEqualTo(BaseUnit.COUNT);
    }

    @Test
    void countableUnitWithOnlyAPackCountStillYieldsAQuantity() {
        // "30 ROLLS" reads as packCount 30 with no per-unit size, because a roll
        // has no size of its own. That is still 30 rolls of product.
        var result = normalizer.normalize(null, "rolls", 30);

        assertThat(result.unit()).isEqualTo(BaseUnit.COUNT);
        assertThat(result.quantity()).isEqualByComparingTo("30");
    }

    @Test
    void aMissingSizeOnAMeasuredUnitIsNotInvented() {
        // Ounces without a number is genuinely unknown; guessing 1 oz would be worse
        // than reporting nothing.
        assertThat(normalizer.normalize(null, "oz", 4).unit()).isEqualTo(BaseUnit.NONE);
    }

    @Test
    void unknownOrMissingSizeYieldsNoBaseUnit() {
        assertThat(normalizer.normalize(new BigDecimal("3"), "furlongs", 1).unit()).isEqualTo(BaseUnit.NONE);
        assertThat(normalizer.normalize(null, "oz", 1).unit()).isEqualTo(BaseUnit.NONE);
        assertThat(normalizer.normalize(new BigDecimal("3"), null, 1).unit()).isEqualTo(BaseUnit.NONE);
    }

    @Test
    void unitPriceDividesCentsByTheNormalizedQuantity() {
        // $7.49 for a 24 ct carton of eggs is 31.2083 cents an egg.
        var size = normalizer.normalize(new BigDecimal("24"), "ct", 1);

        assertThat(normalizer.unitPriceCents(749, size)).isEqualByComparingTo("31.2083");
    }

    @Test
    void unitPriceIsNullWhenTheSizeIsUnknown() {
        assertThat(normalizer.unitPriceCents(749, UnitNormalizer.Normalized.none())).isNull();
    }
}
