package app.pricelog.api.capture;

import static org.assertj.core.api.Assertions.assertThat;

import app.pricelog.api.domain.BaseUnit;
import app.pricelog.api.domain.Category;
import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.domain.Product;
import app.pricelog.api.domain.SaleSignal;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.ProductRepository;
import app.pricelog.api.web.ObservationUpdate;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Corrections change what was asked and leave every other reading of the tag alone. */
@SpringBootTest
@Transactional
class ObservationUpdateTest {

    @Autowired
    ObservationService observations;

    @Autowired
    PriceObservationRepository observationRepo;

    @Autowired
    ProductRepository products;

    @Autowired
    StoreResolver stores;

    @Autowired
    ObjectMapper mapper;

    @Test
    void correctingTheBrandKeepsTheProductInItsComparison() {
        // Logged before commodity had a column, so it only lives in the key. The
        // key used to be rebuilt from the display name on any edit, which moved
        // the product into a group of its own.
        PriceObservation logged = logged("brand", "MEAT|andouille-sausage|plain", null, 999, null);

        PriceObservation fixed = observations.update(logged.getId(), update("{\"brand\":\"Amylu\"}"));

        assertThat(fixed.getProduct().getComparisonKey()).isEqualTo("MEAT|andouille-sausage|plain");
    }

    @Test
    void changingWhatItIsMovesItIntoThatComparison() {
        PriceObservation kirkland = logged("kirkland", "MEAT|chicken-sausage|plain", "chicken sausage", 1299, null);
        PriceObservation amylu = logged("amylu", "MEAT|andouille-sausage|plain", null, 999, 1399);

        PriceObservation fixed = observations.update(amylu.getId(), update("{\"commodity\":\" Chicken  Sausage \"}"));

        assertThat(fixed.getProduct().getCommodity()).isEqualTo("chicken sausage");
        assertThat(fixed.getProduct().getComparisonKey())
                .isEqualTo(kirkland.getProduct().getComparisonKey());
    }

    @Test
    void aRegularPriceAboveThePriceMakesItASaleWhateverTheEnding() {
        PriceObservation logged = logged("sale", "MEAT|sausage|plain", "sausage", 999, null);

        PriceObservation fixed = observations.update(logged.getId(), update("{\"regularPriceCents\":1399}"));

        assertThat(fixed.isOnSale()).isTrue();
        assertThat(fixed.getSaleSignal()).isEqualTo(SaleSignal.INSTANT_SAVINGS);
    }

    @Test
    void notOnSaleClearsTheSaleItHadRecorded() {
        PriceObservation logged = logged("not-sale", "MEAT|sausage|plain", "sausage", 999, 1399);
        logged.setSaleEndsOn(LocalDate.now().plusDays(6));

        PriceObservation fixed = observations.update(logged.getId(), update("{\"onSale\":false}"));

        assertThat(fixed.isOnSale()).isFalse();
        assertThat(fixed.getRegularPriceCents()).isNull();
        assertThat(fixed.getSaleEndsOn()).isNull();
        assertThat(fixed.getSaleSignal()).isEqualTo(SaleSignal.REGULAR);
    }

    @Test
    void sayingItIsRestockingSticksEvenWithTheAsteriskOnTheTag() {
        PriceObservation logged = logged("asterisk", "PANTRY|pistachio-cream|plain", "pistachio cream", 1389, null);
        logged.setRawExtraction("{\"markers\":[\"ASTERISK\"]}");
        logged.setDiscontinued(true);

        PriceObservation fixed = observations.update(logged.getId(), update("{\"discontinued\":false}"));

        assertThat(fixed.isDiscontinued()).isFalse();
    }

    private ObservationUpdate update(String json) {
        return mapper.readValue(json, ObservationUpdate.class);
    }

    private PriceObservation logged(String suffix, String comparisonKey, String commodity,
                                    int priceCents, Integer regularCents) {
        Product product = new Product();
        product.setDisplayName("TEST " + suffix);
        product.setNormalizedKey("update-test-" + suffix);
        product.setComparisonKey(comparisonKey);
        product.setCommodity(commodity);
        product.setCategory(Category.valueOf(comparisonKey.substring(0, comparisonKey.indexOf('|'))));
        product.setSizeValue(new BigDecimal("40"));
        product.setSizeUnit("oz");
        product.setBaseUnit(BaseUnit.OZ);
        product.setBaseQuantity(new BigDecimal("40"));
        product = products.save(product);

        PriceObservation observation = new PriceObservation();
        observation.setProduct(product);
        observation.setStore(stores.forChain(Chain.COSTCO));
        observation.setObservedOn(LocalDate.now());
        observation.setPriceCents(priceCents);
        observation.setRegularPriceCents(regularCents);
        observation.setOnSale(regularCents != null);
        observation.setBaseUnit(BaseUnit.OZ);
        observation.setSaleSignal(SaleSignal.REGULAR);
        return observationRepo.save(observation);
    }
}
