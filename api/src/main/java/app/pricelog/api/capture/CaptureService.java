package app.pricelog.api.capture;

import app.pricelog.api.alerts.PriceHistoryService;
import app.pricelog.api.domain.*;
import app.pricelog.api.extract.*;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.StoreRepository;
import app.pricelog.api.security.UserContext;
import app.pricelog.api.storage.PhotoStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The photo-to-row pipeline: store the image, read the tag, resolve the
 * product, apply the store's tag conventions, and record the observation.
 */
@Service
public class CaptureService {

    private static final Logger log = LoggerFactory.getLogger(CaptureService.class);

    /** Below this the capture is queued for manual confirmation. */
    private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("0.85");

    private final PhotoStore photos;
    private final AzureVisionTagExtractor extractor;
    private final ProductResolver productResolver;
    private final TagRuleEngine ruleEngine;
    private final StoreRepository stores;
    private final StoreResolver storeResolver;
    private final PriceObservationRepository observations;
    private final PriceHistoryService priceHistory;
    private final UserContext users;
    private final ObjectMapper mapper;

    public CaptureService(PhotoStore photos,
                          AzureVisionTagExtractor extractor,
                          ProductResolver productResolver,
                          TagRuleEngine ruleEngine,
                          StoreRepository stores,
                          StoreResolver storeResolver,
                          PriceObservationRepository observations,
                          PriceHistoryService priceHistory,
                          UserContext users,
                          ObjectMapper mapper) {
        this.photos = photos;
        this.extractor = extractor;
        this.productResolver = productResolver;
        this.ruleEngine = ruleEngine;
        this.stores = stores;
        this.storeResolver = storeResolver;
        this.observations = observations;
        this.priceHistory = priceHistory;
        this.users = users;
        this.mapper = mapper;
    }

    /**
     * @param chain   the chain the shopper picked; resolved through the user's
     *                own default store for that chain
     * @param storeId an exact store to log at. Must belong to the current user;
     *                when both are given, this wins over the chain.
     */
    @Transactional
    public CaptureResult capture(byte[] imageBytes,
                                 String contentType,
                                 Chain chain,
                                 Long storeId,
                                 LocalDate observedOn) {

        Long userId = users.userId();

        // An explicit store skips resolution entirely; it has to be the user's.
        Store store = null;
        Chain picked = chain;
        if (storeId != null) {
            store = stores.findByIdAndUserId(storeId, userId)
                    .orElseThrow(() -> new NoSuchElementException("No store " + storeId));
            picked = store.getChain();
        }

        ExtractedTag tag = extractor.extract(imageBytes, contentType, picked == null ? null : picked.name());

        if (Boolean.FALSE.equals(tag.readable()) || tag.price() == null) {
            throw new UnreadableTagException(
                    "Could not read a price from this photo. Try again closer, with the whole tag in frame.");
        }

        // Only keep the photo once the tag is known to be usable.
        String photoKey = photos.store(imageBytes, contentType, "tag");
        // Blob writes are outside the transaction, so a later failure would
        // strand the file with no row pointing at it. Undo it on rollback.
        deletePhotoIfRolledBack(photoKey);

        if (store == null) {
            store = storeResolver.forChain(
                    userId, picked != null ? picked : Enums.parseOr(Chain.class, tag.storeChain(), Chain.OTHER));
        }

        Product product = productResolver.resolve(tag);

        int priceCents = toCents(tag.price());
        Integer regularCents = regularPriceCents(tag, priceCents);
        boolean tagShowsSaving = regularCents != null
                || Boolean.TRUE.equals(tag.onSale())
                || (tag.savingsAmount() != null && tag.savingsAmount().signum() > 0);

        TagVerdict verdict = ruleEngine.evaluate(
                userId, store.getChain(), priceCents, tag.markersOrEmpty(), tag.rawText(), tagShowsSaving);

        PriceObservation observation = new PriceObservation();
        observation.setUserId(userId);
        observation.setProduct(product);
        observation.setStore(store);
        observation.setObservedOn(observedOn == null ? LocalDate.now() : observedOn);
        observation.setPriceCents(priceCents);
        observation.setRegularPriceCents(regularCents);
        observation.setOnSale(tagShowsSaving || verdict.discounted());
        observation.setSaleSignal(verdict.signal());
        observation.setSaleEndsOn(parseDate(tag.saleEndsOn()));
        observation.setDiscontinued(verdict.discontinued());
        observation.setUnitPriceCents(productResolver.unitPriceCents(priceCents, product));
        observation.setBaseUnit(product.getBaseUnit());
        observation.setItemNumber(tag.itemNumber());
        observation.setPhotoUrl(photoKey);
        observation.setConfidence(tag.confidence());
        observation.setNeedsReview(needsReview(tag, product));
        observation.setRawExtraction(toJson(tag));
        observation.setTagInsights(verdict.matched());
        observation.setAdvice(verdict.advice());

        // Must run before the save, while "previous" still excludes this sighting.
        priceHistory.attachHistory(userId, observation);

        observations.save(observation);

        return new CaptureResult(observation, verdict, tag);
    }

    /** The price before the saving, when the tag shows one that is actually higher. */
    private Integer regularPriceCents(ExtractedTag tag, int priceCents) {
        Integer regular = null;
        if (tag.regularPrice() != null) {
            regular = toCents(tag.regularPrice());
        } else if (tag.savingsAmount() != null && tag.savingsAmount().signum() > 0) {
            // Tag stated a savings amount but not the original price; reconstruct it.
            regular = priceCents + toCents(tag.savingsAmount());
        }
        return regular != null && regular > priceCents ? regular : null;
    }

    /** Anything the model was unsure of, or could not size, gets a human look. */
    private boolean needsReview(ExtractedTag tag, Product product) {
        if (tag.confidence() == null || tag.confidence().compareTo(REVIEW_THRESHOLD) < 0) {
            return true;
        }
        return product.getBaseUnit() == null || product.getBaseUnit() == BaseUnit.NONE;
    }

    private int toCents(BigDecimal dollars) {
        return dollars.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            log.debug("Ignoring unparseable sale end date from tag: {}", raw);
            return null;
        }
    }

    private void deletePhotoIfRolledBack(String photoKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    photos.delete(photoKey);
                } catch (RuntimeException e) {
                    log.warn("Capture failed and its photo {} could not be cleaned up", photoKey, e);
                }
            }
        });
    }

    private String toJson(ExtractedTag tag) {
        try {
            return mapper.writeValueAsString(tag);
        } catch (JacksonException e) {
            log.debug("Could not serialize raw extraction", e);
            return null;
        }
    }
}
