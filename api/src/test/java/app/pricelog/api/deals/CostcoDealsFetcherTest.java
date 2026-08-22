package app.pricelog.api.deals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The fixture mirrors the real page: the product name appears twice around the
 * availability markers, the discount is split across a bare "$" and a number,
 * and one offer spans two item numbers.
 */
class CostcoDealsFetcherTest {

    private final CostcoDealsFetcher fetcher = new CostcoDealsFetcher("http://unused");

    private static final String PAGE = """
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
              <span>Online</span>
              <span>Online Only Treasure Chest</span>
              <span>Item 4455661</span>
              <span>After $25.50 OFF</span>
            </div>
            """;

    @Test
    void keepsTheSalePriceAndTheAmountOffApart() {
        // The page prints "$23.99" as the promotional price and "After $6 OFF"
        // as the saving. Reading the first as a discount would turn a $6 saving
        // into a $23 one and imply the item costs $1.99.
        PublishedDeal charmin = fetcher.parse(PAGE).stream()
                .filter(d -> d.itemNumbers().contains("2048748"))
                .findFirst()
                .orElseThrow();

        assertThat(charmin.title()).isEqualTo("Charmin Ultra Soft Bath Tissue");
        assertThat(charmin.salePriceCents()).isEqualTo(2399);
        assertThat(charmin.discountCents()).isEqualTo(600);
    }

    @Test
    void readsAPriceSplitAcrossLinesWithNoCents() {
        PublishedDeal sock = fetcher.parse(PAGE).stream()
                .filter(d -> d.itemNumbers().contains("1927653"))
                .findFirst()
                .orElseThrow();

        assertThat(sock.title()).isEqualTo("adidas Men's Quarter Sock");
        assertThat(sock.salePriceCents()).isEqualTo(900);
        assertThat(sock.inWarehouse()).isTrue();
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
        PublishedDeal chest = fetcher.parse(PAGE).stream()
                .filter(d -> d.itemNumbers().contains("4455661"))
                .findFirst()
                .orElseThrow();

        assertThat(chest.discountCents()).isEqualTo(2550);
        // Only an amount off was printed, so there is no price to claim.
        assertThat(chest.salePriceCents()).isNull();
    }

    @Test
    void marksOnlineOnlyOffersAsNotValidInWarehouse() {
        PublishedDeal chest = fetcher.parse(PAGE).stream()
                .filter(d -> d.itemNumbers().contains("4455661"))
                .findFirst()
                .orElseThrow();

        // Useless to someone standing in a warehouse, so it must be separable.
        assertThat(chest.inWarehouse()).isFalse();
    }

    @Test
    void neverMistakesFinePrintOrSizesForTheProductName() {
        assertThat(fetcher.parse(PAGE))
                .extracting(PublishedDeal::title)
                .noneMatch(t -> t.startsWith("Limit ") || t.equals("6 pair"));
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
