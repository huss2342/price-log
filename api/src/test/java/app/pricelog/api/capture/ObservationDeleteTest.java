package app.pricelog.api.capture;

import static org.assertj.core.api.Assertions.assertThat;

import app.pricelog.api.domain.BaseUnit;
import app.pricelog.api.domain.Category;
import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.domain.Product;
import app.pricelog.api.domain.SaleSignal;
import app.pricelog.api.domain.Store;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.ProductRepository;
import app.pricelog.api.repo.StoreRepository;
import app.pricelog.api.storage.PhotoStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Deleting a reading has to take its photo with it. Six orphaned blobs in
 * production came from this, each one outliving every row that referenced it.
 *
 * <p>Deliberately not {@code @Transactional}: the cleanup runs after commit, so
 * a rolled-back test transaction would never fire it.
 */
@SpringBootTest
class ObservationDeleteTest {

    @Autowired
    ObservationService observations;

    @Autowired
    PriceObservationRepository observationRepo;

    @Autowired
    ProductRepository products;

    @Autowired
    StoreRepository stores;

    @Autowired
    PhotoStore photos;

    @Test
    void deletingAnObservationAlsoDeletesItsPhoto() {
        String photoKey = photos.store("not really a jpeg".getBytes(), "image/jpeg", "tag");
        assertThat(photos.load(photoKey)).isPresent();

        PriceObservation saved = observationRepo.save(observationOn(photoKey, "with-photo"));

        observations.delete(saved.getId());

        assertThat(observationRepo.findById(saved.getId())).isEmpty();
        assertThat(photos.load(photoKey)).isEmpty();
    }

    /** A missing photo must not block the row from being deleted. */
    @Test
    void deletingSurvivesAnAlreadyMissingPhoto() {
        PriceObservation saved = observationRepo.save(
                observationOn("2020-01-01/gone.jpg", "missing-photo"));

        observations.delete(saved.getId());

        assertThat(observationRepo.findById(saved.getId())).isEmpty();
    }

    private PriceObservation observationOn(String photoKey, String suffix) {
        Store store = stores.save(new Store(Chain.COSTCO, "Test warehouse " + suffix, null, null));

        Product product = new Product();
        product.setDisplayName("TEST ITEM");
        product.setNormalizedKey("test-item-" + suffix);
        product.setComparisonKey("PANTRY|test-item|plain");
        product.setCategory(Category.PANTRY);
        product.setBaseUnit(BaseUnit.COUNT);
        product.setBaseQuantity(new BigDecimal("1"));
        product = products.save(product);

        PriceObservation observation = new PriceObservation();
        observation.setProduct(product);
        observation.setStore(store);
        observation.setObservedOn(LocalDate.now());
        observation.setPriceCents(1000);
        observation.setUnitPriceCents(new BigDecimal("1000"));
        observation.setBaseUnit(BaseUnit.COUNT);
        observation.setSaleSignal(SaleSignal.REGULAR);
        observation.setPhotoUrl(photoKey);
        return observation;
    }
}
