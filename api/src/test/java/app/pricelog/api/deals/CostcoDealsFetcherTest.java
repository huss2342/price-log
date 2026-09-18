package app.pricelog.api.deals;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The fixture mirrors the real page: the product name appears twice around the
 * availability markers, amounts are split across bare "$" and number nodes, one
 * offer spans two item numbers, and some offers state only a saving.
 */
class CostcoDealsFetcherTest {

    private final CostcoDealsFetcher fetcher = new CostcoDealsFetcher("http://unused");

    private static final String PAGE = """
            <div>
              <span>Pricing shown is warehouse pricing</span>
              <span>Pricing may vary by location | Valid 8/24/26 - 9/20/26</span>
            </div>
            <div>
              <span>Buy Online</span>
              <span>Charmin Ultra Soft Bath Tissue</span>
              <span>Warehouse</span><span>&amp;</span><span>Online</span>
              <span>Charmin Ultra Soft Bath Tissue</span>
              <span>30/197 sheets</span>
              <span>Item 2048748</span>
              <span>Limit 2.</span>
              <span>$</span><span>23</span><span>.</span><span>99</span>
              <span>After $6 OFF</span>
            </div>
            <div>
              <span>Buy Online</span>
              <span>adidas Men's Quarter Sock</span>
              <span>Warehouse</span><span>&amp;</span><span>Online</span>
              <span>adidas Men's Quarter Sock</span>
              <span>6 pair</span>
              <span>Item 1927653</span>
              <span>Limit 10. Selection varies by location.</span>
              <span>$</span><span>9</span>
            </div>
            <div>
              <span>adidas Men's Tricot Jacket AND/OR Pant</span>
              <span>Warehouse</span><span>&amp;</span><span>Online</span>
              <span>adidas Men's Tricot Jacket AND/OR Pant</span>
              <span>Item 1896539, 1896546</span>
              <span>Limit 10 Each.</span>
              <span>$</span><span>14</span>
            </div>
            <div>
              <span>Online Only Treasure Chest</span>
              <span>Online Only</span>
              <span>Online Only Treasure Chest</span>
              <span>Item 4455661</span>
              <span>After $25.50 OFF</span>
            </div>
            <div>
              <span>Amylu Paleo Andouille Chicken Sausages</span>
              <span>Warehouse Only</span>
              <span>Amylu Paleo Andouille Chicken Sausages</span>
              <span>40 oz</span>
              <span>Item 1451835</span>
              <span>Save</span><span>$</span><span>4</span>
            </div>
            <div>
              <span>Hillshire Farm Naturals Turkey Breast</span>
              <span>Warehouse Only</span>
              <span>Hillshire Farm Naturals Turkey Breast</span>
              <span>2/16.5 oz</span>
              <span>Item 947880</span>
              <span>After $4 OFF</span>
            </div>
            <div>
              <span>Buy Online</span>
              <span>Kirkland Signature Micellar Facial Cleansing Wipes</span>
              <span>Warehouse</span><span>&amp;</span><span>Online</span>
              <span>Kirkland Signature Micellar Facial Cleansing Wipes</span>
              <span>180 ct</span>
              <span>Item 1931211</span>
              <span>Valid through 8/30/26.</span>
              <span>Save</span><span>$</span><span>3</span>
            </div>
            """;

    private PublishedDeal deal(String itemNumber) {
        return fetcher.parse(PAGE).stream()
                .filter(d -> d.itemNumbers().contains(itemNumber))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void keepsTheSalePriceAndTheAmountOffApart() {
        // The page prints "$23.99" as the promotional price and "After $6 OFF"
        // as the saving. Reading the first as a discount would turn a $6 saving
        // into a $23 one and imply the item costs $1.99.
        PublishedDeal charmin = deal("2048748");

        assertThat(charmin.title()).isEqualTo("Charmin Ultra Soft Bath Tissue");
        assertThat(charmin.salePriceCents()).isEqualTo(2399);
        assertThat(charmin.discountCents()).isEqualTo(600);
    }

    @Test
    void anAmountAfterSaveIsTheSavingNotThePrice() {
        // Printed as "Save", "$", "4". Read as a price, this advertised 40 oz of
        // sausages at $4 when the tag in the warehouse said $9.99 after $4 off.
        PublishedDeal amylu = deal("1451835");

        assertThat(amylu.discountCents()).isEqualTo(400);
        assertThat(amylu.salePriceCents()).isNull();
    }

    @Test
    void warehouseOnlyOffersAreValidInTheWarehouse() {
        assertThat(deal("1451835").inWarehouse()).isTrue();
        assertThat(deal("2048748").inWarehouse()).isTrue();
    }

    @Test
    void marksOnlineOnlyOffersAsNotValidInWarehouse() {
        // Useless to someone standing in a warehouse, so it must be separable.
        assertThat(deal("4455661").inWarehouse()).isFalse();
    }

    @Test
    void readsAPriceSplitAcrossLinesWithNoCents() {
        PublishedDeal sock = deal("1927653");

        assertThat(sock.title()).isEqualTo("adidas Men's Quarter Sock");
        assertThat(sock.salePriceCents()).isEqualTo(900);
    }

    @Test
    void figuresNeverBleedIntoTheNextOffer() {
        // The sock states only a price; the offers below it state savings. Each
        // keeps its own.
        assertThat(deal("1927653").discountCents()).isNull();
        assertThat(deal("1451835").salePriceCents()).isNull();
        assertThat(deal("947880").discountCents()).isEqualTo(400);
    }

    @Test
    void oneOfferCanCoverSeveralItemNumbers() {
        PublishedDeal tricot = fetcher.parse(PAGE).stream()
                .filter(d -> d.title().startsWith("adidas Men's Tricot"))
                .findFirst()
                .orElseThrow();

        assertThat(tricot.itemNumbers()).containsExactly("1896539", "1896546");
        assertThat(tricot.salePriceCents()).isEqualTo(1400);
    }

    @Test
    void readsTheWrittenOutDiscountFormAndItsDecimals() {
        PublishedDeal chest = deal("4455661");

        assertThat(chest.discountCents()).isEqualTo(2550);
        // Only an amount off was printed, so there is no price to claim.
        assertThat(chest.salePriceCents()).isNull();
    }

    @Test
    void offersRunUntilTheBookletEndsUnlessTheyEndSooner() {
        assertThat(deal("1451835").validUntil()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(deal("1931211").validUntil()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(deal("1931211").discountCents()).isEqualTo(300);
    }

    @Test
    void neverMistakesFinePrintOrSizesForTheProductName() {
        assertThat(fetcher.parse(PAGE))
                .extracting(PublishedDeal::title)
                .noneMatch(t -> t.startsWith("Limit ") || t.equals("6 pair") || t.startsWith("Valid"));
    }

    @Test
    void decodesNumericEntitiesInProductNames() {
        // Costco writes apostrophes as &#x27;, which would otherwise survive
        // into every possessive brand name.
        String page = """
                <div>
                  <span>Kirkland Signature Men&#x27;s Sock</span>
                  <span>Warehouse</span>
                  <span>Kirkland Signature Men&#x27;s Sock</span>
                  <span>Item 1234567</span>
                  <span>$</span><span>5</span><span>.</span><span>99</span>
                </div>
                """;

        assertThat(fetcher.parse(page)).singleElement()
                .extracting(PublishedDeal::title)
                .isEqualTo("Kirkland Signature Men's Sock");
    }

    @Test
    void aPageThatCannotBeParsedYieldsNothingRatherThanGarbage() {
        assertThat(fetcher.parse("<html><body>Access Denied</body></html>")).isEmpty();
    }
}
