package app.pricelog.api.capture;

import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.domain.Product;
import app.pricelog.api.domain.UserProductWatch;
import app.pricelog.api.extract.ExtractedTag;
import app.pricelog.api.extract.ProductResolver;
import app.pricelog.api.extract.TagRuleEngine;
import app.pricelog.api.extract.TagVerdict;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.ProductRepository;
import app.pricelog.api.repo.StoreRepository;
import app.pricelog.api.repo.UserProductWatchRepository;
import app.pricelog.api.security.UserContext;
import app.pricelog.api.storage.PhotoStore;
import app.pricelog.api.web.ObservationUpdate;
import app.pricelog.api.web.ObservationView;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ObservationService {

    private static final Logger log = LoggerFactory.getLogger(ObservationService.class);

    private final PriceObservationRepository observations;
    private final ProductRepository products;
    private final StoreRepository stores;
    private final StoreResolver storeResolver;
    private final ProductResolver productResolver;
    private final TagRuleEngine ruleEngine;
    private final PhotoStore photos;
    private final UserProductWatchRepository watches;
    private final UserContext users;
    private final ObjectMapper mapper;

    public ObservationService(PriceObservationRepository observations,
                              ProductRepository products,
                              StoreRepository stores,
                              StoreResolver storeResolver,
                              ProductResolver productResolver,
                              TagRuleEngine ruleEngine,
                              PhotoStore photos,
                              UserProductWatchRepository watches,
                              UserContext users,
                              ObjectMapper mapper) {
        this.observations = observations;
        this.products = products;
        this.stores = stores;
        this.storeResolver = storeResolver;
        this.productResolver = productResolver;
        this.ruleEngine = ruleEngine;
        this.photos = photos;
        this.watches = watches;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<PriceObservation> pendingReview() {
        return observations.findPendingReview(users.userId());
    }

    @Transactional(readOnly = true)
    public List<PriceObservation> recent(int limit) {
        return observations.findRecent(users.userId(), PageRequest.of(0, limit));
    }

    /** The whole log, newest first. One person's shopping stays small enough to send at once. */
    @Transactional(readOnly = true)
    public List<PriceObservation> all() {
        return observations.findAllNewestFirst(users.userId());
    }

    @Transactional(readOnly = true)
    public Page<PriceObservation> page(Pageable pageable) {
        return observations.pageByUserId(users.userId(), pageable);
    }

    @Transactional(readOnly = true)
    public PriceObservation get(Long id) {
        return observations.findByIdAndUserId(id, users.userId())
                .orElseThrow(() -> new NoSuchElementException("No observation " + id));
    }

    /**
     * Flattens rows for the UI, resolving each product's watch state for the
     * current user in one query rather than one per row.
     */
    @Transactional(readOnly = true)
    public List<ObservationView> views(List<PriceObservation> rows) {
        Long userId = users.userId();
        Map<Long, UserProductWatch> byProduct = watches
                .findByUserIdAndProductIdIn(userId, productIds(rows)).stream()
                .collect(Collectors.toMap(UserProductWatch::getProductId, Function.identity()));
        return rows.stream()
                .map(o -> ObservationView.of(o, watchState(byProduct.get(o.getProduct().getId()))))
                .toList();
    }

    private List<Long> productIds(List<PriceObservation> rows) {
        return rows.stream().map(o -> o.getProduct().getId()).distinct().toList();
    }

    private ObservationView.WatchState watchState(UserProductWatch watch) {
        return watch == null
                ? ObservationView.WatchState.NONE
                : new ObservationView.WatchState(watch.isWatched(), watch.getTargetPriceCents());
    }

    /** The current user's watch state for specific products. */
    @Transactional(readOnly = true)
    public Map<Long, UserProductWatch> watchesFor(Collection<Long> productIds) {
        Long userId = users.userId();
        return watches.findByUserIdAndProductIdIn(userId, productIds).stream()
                .collect(Collectors.toMap(UserProductWatch::getProductId, Function.identity()));
    }

    @Transactional
    public PriceObservation update(Long id, ObservationUpdate update) {
        Long userId = users.userId();
        PriceObservation observation = observations.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("No observation " + id));
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
        if (update.commodity() != null && !update.commodity().isBlank()) {
            product.setCommodity(ProductResolver.normalizeCommodity(update.commodity()));
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

        if (update.chain() != null) {
            observation.setStore(storeResolver.forChain(userId, update.chain()));
        } else if (update.storeId() != null) {
            observation.setStore(stores.findByIdAndUserId(update.storeId(), userId)
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
            if (!update.onSale()) {
                observation.setRegularPriceCents(null);
                observation.setSaleEndsOn(null);
            }
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
            reapplyTagRules(userId, observation, update.onSale() == null, update.discontinued() == null);
        }

        return observations.save(observation);
    }

    /**
     * Re-runs the store's tag conventions against the current price, reusing the
     * markers and raw text captured from the original photo.
     *
     * @param refreshOnSale       false when the user set the sale flag by hand, so
     *                            their answer is not overwritten
     * @param refreshDiscontinued false when the user said whether it is being
     *                            restocked, for the same reason
     */
    private void reapplyTagRules(Long userId, PriceObservation observation, boolean refreshOnSale,
                                 boolean refreshDiscontinued) {
        ExtractedTag tag = parseExtraction(observation.getRawExtraction());
        List<String> markers = tag == null ? List.of() : tag.markersOrEmpty();
        String rawText = tag == null ? null : tag.rawText();

        Integer regular = observation.getRegularPriceCents();
        boolean showsSaving = regular != null && regular > observation.getPriceCents()
                || (!refreshOnSale && observation.isOnSale());

        TagVerdict verdict = ruleEngine.evaluate(userId, observation.getStore().getChain(),
                observation.getPriceCents(), markers, rawText, showsSaving);

        observation.setSaleSignal(verdict.signal());
        observation.setTagInsights(verdict.matched());
        observation.setAdvice(verdict.advice());

        if (refreshDiscontinued) {
            observation.setDiscontinued(verdict.discontinued());
        }
        if (refreshOnSale) {
            observation.setOnSale(verdict.discounted());
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

    /**
     * Deletes the observation and the photo it was read from. Without this the
     * blob outlives every row that referenced it, and nothing ever collects it.
     * The file is removed only after the delete commits, so a rolled-back
     * transaction cannot destroy a photo its row still points at.
     */
    @Transactional
    public void delete(Long id) {
        PriceObservation observation = observations.findByIdAndUserId(id, users.userId())
                .orElseThrow(() -> new NoSuchElementException("No observation " + id));
        String photoKey = observation.getPhotoUrl();
        observations.delete(observation);
        if (photoKey != null && !photoKey.isBlank()) {
            afterCommit(() -> removePhoto(photoKey));
        }
    }

    /**
     * Storage is not transactional, so photo cleanup is deferred to commit and
     * its failures are logged rather than thrown: the row is already gone, and
     * a stranded file is not worth failing the caller's request over.
     */
    private void removePhoto(String key) {
        try {
            photos.delete(key);
        } catch (RuntimeException e) {
            log.warn("Deleted the observation but could not delete its photo {}", key, e);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    @Transactional(readOnly = true)
    public java.util.List<Product> allProducts() {
        return products.findAll();
    }
}
