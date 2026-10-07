package app.pricelog.api.extract;

import static org.assertj.core.api.Assertions.assertThat;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.SaleSignal;
import app.pricelog.api.security.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Exercises the seeded tag conventions against the prices they describe. */
@SpringBootTest
class TagRuleEngineTest {

    @Autowired
    private TagRuleEngine engine;

    @Autowired
    private UserRepository users;

    private Long ownerId;

    @org.junit.jupiter.api.BeforeEach
    void resolveOwner() {
        ownerId = users.findFirstByOrderByIdAsc().orElseThrow().getId();
    }

    @Test
    void costcoNinetyNineIsJustTheRegularPrice() {
        var verdict = engine.evaluate(ownerId, Chain.COSTCO, 1299, List.of(), "KS ORGANIC EGGS 24 CT");

        assertThat(verdict.signal()).isEqualTo(SaleSignal.REGULAR);
        assertThat(verdict.discontinued()).isFalse();
    }

    @Test
    void costcoNinetySevenIsAStoreClearanceMarkdown() {
        var verdict = engine.evaluate(ownerId, Chain.COSTCO, 897, List.of(), "");

        assertThat(verdict.signal()).isEqualTo(SaleSignal.CLEARANCE);
        assertThat(verdict.advice()).isNotBlank();
    }

    @Test
    void costcoAsteriskOutranksThePriceEndingAndFlagsDiscontinued() {
        // .97 alone means clearance, but the asterisk is the more important
        // signal: the item is not coming back at any price.
        var verdict = engine.evaluate(ownerId, Chain.COSTCO, 897, List.of("ASTERISK"), "");

        assertThat(verdict.signal()).isEqualTo(SaleSignal.DISCONTINUED);
        assertThat(verdict.discontinued()).isTrue();
        assertThat(verdict.matched()).hasSizeGreaterThan(1);
        // The asterisk wins the headline, but the .97 markdown still means the
        // price was cut, so the observation must not be recorded at full price.
        assertThat(verdict.discounted()).isTrue();
    }

    @Test
    void aNinetyNineWithASavingPrintedBesideItIsASaleNotFullPrice() {
        // $13.99 less $4 is $9.99. The .99 ending alone reads as an everyday
        // price, which once labelled a live instant saving "full price".
        var verdict = engine.evaluate(ownerId, Chain.COSTCO, 999, List.of(), "", true);

        assertThat(verdict.signal()).isEqualTo(SaleSignal.INSTANT_SAVINGS);
        assertThat(verdict.discounted()).isTrue();
        assertThat(verdict.matched()).noneMatch(m -> m.contains("standard everyday price"));
    }

    @Test
    void aSavingDoesNotHideThatTheItemIsNotRestocking() {
        var verdict = engine.evaluate(ownerId, Chain.COSTCO, 999, List.of("ASTERISK"), "", true);

        assertThat(verdict.discontinued()).isTrue();
        assertThat(verdict.discounted()).isTrue();
    }

    @Test
    void regularPricedItemIsNotMarkedDiscounted() {
        var verdict = engine.evaluate(ownerId, Chain.COSTCO, 1299, List.of(), "");

        assertThat(verdict.discounted()).isFalse();
    }

    @Test
    void costcoDoubleZeroIsTheDeepestManagerMarkdown() {
        var verdict = engine.evaluate(ownerId, Chain.COSTCO, 1500, List.of(), "");

        assertThat(verdict.signal()).isEqualTo(SaleSignal.MANAGER_MARKDOWN);
    }

    @Test
    void samsClubOneCentEndingIsTheFinalMarkdown() {
        var verdict = engine.evaluate(ownerId, Chain.SAMS_CLUB, 1201, List.of(), "");

        assertThat(verdict.signal()).isEqualTo(SaleSignal.CLEARANCE);
    }

    @Test
    void samsClubCMarkerBeatsThePriceEnding() {
        var verdict = engine.evaluate(ownerId, Chain.SAMS_CLUB, 1200, List.of("C_MARKER"), "");

        assertThat(verdict.signal()).isEqualTo(SaleSignal.CLEARANCE);
    }

    @Test
    void walmartRollbackIsMatchedFromTheTagText() {
        var verdict = engine.evaluate(ownerId, Chain.WALMART, 1497, List.of(), "Rollback  was $19.97");

        assertThat(verdict.signal()).isEqualTo(SaleSignal.INSTANT_SAVINGS);
    }

    @Test
    void rulesAreScopedToTheirOwnChain() {
        // .97 is a Costco convention and must not fire on a Sam's Club tag.
        var verdict = engine.evaluate(ownerId, Chain.SAMS_CLUB, 897, List.of(), "");

        assertThat(verdict.signal()).isNotEqualTo(SaleSignal.CLEARANCE);
    }

    @Test
    void unrecognisedPatternsReturnUnknownRatherThanGuessing() {
        var verdict = engine.evaluate(ownerId, Chain.OTHER, 1234, List.of(), "some corner store");

        assertThat(verdict.signal()).isEqualTo(SaleSignal.UNKNOWN);
        assertThat(verdict.matched()).isEmpty();
    }
}
