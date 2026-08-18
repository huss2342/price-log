package app.pricelog.api.capture;

import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.domain.Product;
import app.pricelog.api.extract.ExtractedTag;
import app.pricelog.api.extract.ProductResolver;
import app.pricelog.api.extract.TagRuleEngine;
import app.pricelog.api.extract.TagVerdict;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.ProductRepository;
import app.pricelog.api.repo.StoreRepository;
import app.pricelog.api.web.ObservationUpdate;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ObservationService {

    private final PriceObservationRepository observations;
    private final ProductRepository products;
    private final StoreRepository stores;
    private final ProductResolver productResolver;
    private final TagRuleEngine ruleEngine;
    private final ObjectMapper mapper;

    public ObservationService(PriceObservationRepository observations,
                              ProductRepository products,
                              StoreRepository stores,
                              ProductResolver productResolver,
                              TagRuleEngine ruleEngine,
                              ObjectMapper mapper) {
        this.observations = observations;
        this.products = products;
        this.stores = stores;
        this.productResolver = productResolver;
        this.ruleEngine = ruleEngine;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public java.util.List<PriceObservation> pendingReview() {
        return observations.findPendingReview();
    }

    @Transactional(readOnly = true)
    public java.util.List<PriceObservation> recent(int limit) {
        return observations.findRecent(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public PriceObservation get(Long id) {
        return observations.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No observation " + id));
    }

    @Transactional
    public PriceObservation update(Long id, ObservationUpdate update) {
        PriceObservation observation = get(id);
        Product product = observation.getProduct();
        boolean productChanged = false;

        if (update.productName() != null && !update.productName().isBlank()) {
            product.setDisplayName(update.productName().trim());
            productChanged = true;
        }
        if (update.brand() != null) {
            product.setBrand(update.brand().isBlank() ? null : update.brand().trim());
            productChanged = true;
        }
        if (update.category() != null) {
            product.setCategory(update.category());
            productChanged = true;
        }
        if (update.attributes() != null) {
            product.setAttributes(update.attributes());
            productChanged = true;
        }
        if (update.sizeValue() != null) {
            product.setSizeValue(update.sizeValue());
            productChanged = true;
        }
        if (update.sizeUnit() != null) {
            product.setSizeUnit(update.sizeUnit().isBlank() ? null : update.sizeUnit().trim());
            productChanged = true;
        }
        if (update.packCount() != null) {
            product.setPackCount(update.packCount());
            productChanged = true;
        }

        if (productChanged) {
            // Recomputes base quantity and the comparison key. The normalized key is
            // left alone on purpose so existing observations keep pointing here.
            product = productResolver.recompute(product);
            observation.setProduct(product);
        }

        if (update.storeId() != null) {
            observation.setStore(stores.findById(update.storeId())
                    .orElseThrow(() -> new NoSuchElementException("No store " + update.storeId())));
        }
        if (update.observedOn() != null) {
            observation.setObservedOn(update.observedOn());
        }
        if (update.priceCents() != null) {
            observation.setPriceCents(update.priceCents());
        }
        if (update.regularPriceCents() != null) {
            observation.setRegularPriceCents(update.regularPriceCents());
        }
        if (update.onSale() != null) {
            observation.setOnSale(update.onSale());
        }
        if (update.saleSignal() != null) {
            observation.setSaleSignal(update.saleSignal());
        }
        if (update.saleEndsOn() != null) {
            observation.setSaleEndsOn(update.saleEndsOn());
        }
        if (update.discontinued() != null) {
            observation.setDiscontinued(update.discontinued());
        }
        if (update.itemNumber() != null) {
            observation.setItemNumber(update.itemNumber().isBlank() ? null : update.itemNumber().trim());
        }
        if (update.notes() != null) {
            observation.setNotes(update.notes().isBlank() ? null : update.notes());
        }
        if (Boolean.TRUE.equals(update.reviewed())) {
            observation.setNeedsReview(false);
        }

        // Price or size may have moved, so the unit price is always rebuilt.
        observation.setUnitPriceCents(
                productResolver.unitPriceCents(observation.getPriceCents(), product));
        observation.setBaseUnit(product.getBaseUnit());

        // A corrected price can change its ending, and with it the whole reading
        // of the tag. Only re-derive when the user did not state a signal.
        if (update.saleSignal() == null) {
            reapplyTagRules(observation, update.onSale() == null);
        }

        return observations.save(observation);
    }

    /**
     * Re-runs the store's tag conventions against the current price, reusing the
     * markers and raw text captured from the original photo.
     *
     * @param refreshOnSale false when the user set the sale flag by hand, so
     *                      their answer is not overwritten
     */
    private void reapplyTagRules(PriceObservation observation, boolean refreshOnSale) {
        ExtractedTag tag = parseExtraction(observation.getRawExtraction());
        List<String> markers = tag == null ? List.of() : tag.markersOrEmpty();
        String rawText = tag == null ? null : tag.rawText();

        TagVerdict verdict = ruleEngine.evaluate(
                observation.getStore().getChain(), observation.getPriceCents(), markers, rawText);

        observation.setSaleSignal(verdict.signal());
        observation.setDiscontinued(verdict.discontinued());
        observation.setTagInsights(verdict.matched());
        observation.setAdvice(verdict.advice());

        if (refreshOnSale) {
            observation.setOnSale(verdict.discounted() || observation.getRegularPriceCents() != null);
        }
    }

    private ExtractedTag parseExtraction(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, ExtractedTag.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    @Transactional
    public void delete(Long id) {
        observations.deleteById(id);
    }

    @Transactional(readOnly = true)
    public java.util.List<Product> allProducts() {
        return products.findAll();
    }
}
