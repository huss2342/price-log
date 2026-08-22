package app.pricelog.api.capture;

import app.pricelog.api.alerts.PriceHistoryService;
import app.pricelog.api.domain.*;
import app.pricelog.api.extract.*;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.StoreRepository;
import app.pricelog.api.storage.PhotoStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final PriceObservationRepository observations;
    private final PriceHistoryService priceHistory;
    private final ObjectMapper mapper;

    public CaptureService(PhotoStore photos,
                          AzureVisionTagExtractor extractor,
                          ProductResolver productResolver,
                          TagRuleEngine ruleEngine,
                          StoreRepository stores,
                          PriceObservationRepository observations,
                          PriceHistoryService priceHistory,
                          ObjectMapper mapper) {
        this.photos = photos;
        this.extractor = extractor;
        this.productResolver = productResolver;
        this.ruleEngine = ruleEngine;
        this.stores = stores;
        this.observations = observations;
        this.priceHistory = priceHistory;
        this.mapper = mapper;
    }

    @Transactional
    public CaptureResult capture(byte[] imageBytes,
                                 String contentType,
                                 Long storeId,
                                 LocalDate observedOn) {

        Store store = storeId == null ? null : stores.findById(storeId).orElse(null);
        String chainHint = store == null ? null : store.getChain().name();

        ExtractedTag tag = extractor.extract(imageBytes, contentType, chainHint);

        if (Boolean.FALSE.equals(tag.readable()) || tag.price() == null) {
            throw new UnreadableTagException(
                    "Could not read a price from this photo. Try again closer, with the whole tag in frame.");
        }

        // Only keep the photo once the tag is known to be usable.
        String photoKey = photos.store(imageBytes, contentType, "tag");

        if (store == null) {
            store = resolveStoreFromTag(tag);
        }

        Product product = productResolver.resolve(tag);

        int priceCents = toCents(tag.price());
        Integer regularCents = regularPriceCents(tag, priceCents);

        TagVerdict verdict = ruleEngine.evaluate(
                store.getChain(), priceCents, tag.markersOrEmpty(), tag.rawText());

        PriceObservation observation = new PriceObservation();
        observation.setProduct(product);
        observation.setStore(store);
        observation.setObservedOn(observedOn == null ? LocalDate.now() : observedOn);
        observation.setPriceCents(priceCents);
        observation.setRegularPriceCents(regularCents);
        observation.setOnSale(isOnSale(tag, regularCents, verdict));
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
        priceHistory.attachHistory(observation);

        observations.save(observation);

        return new CaptureResult(observation, verdict, tag);
    }

    /**
     * No store was picked, so fall back to a chain-level placeholder. The tag
     * conventions only depend on the chain, so this still produces the right
     * reading; the user can attach a real location on the review screen.
     */
    private Store resolveStoreFromTag(ExtractedTag tag) {
        Chain chain = Enums.parseOr(Chain.class, tag.storeChain(), Chain.OTHER);
        String label = defaultLabelFor(chain);
        return stores.findByChainAndLabel(chain, label)
                .orElseGet(() -> stores.save(new Store(chain, label, null, null)));
    }

    private String defaultLabelFor(Chain chain) {
        return switch (chain) {
            case COSTCO -> "Costco (unspecified)";
            case SAMS_CLUB -> "Sam's Club (unspecified)";
            case ALDI -> "Aldi (unspecified)";
            case WALMART -> "Walmart (unspecified)";
            case OTHER -> "Unknown store";
        };
    }

    private Integer regularPriceCents(ExtractedTag tag, int priceCents) {
        if (tag.regularPrice() != null) {
            return toCents(tag.regularPrice());
        }
        // Tag stated a savings amount but not the original price; reconstruct it.
        if (tag.savingsAmount() != null && tag.savingsAmount().signum() > 0) {
            return priceCents + toCents(tag.savingsAmount());
        }
        return null;
    }

    private boolean isOnSale(ExtractedTag tag, Integer regularCents, TagVerdict verdict) {
        return Boolean.TRUE.equals(tag.onSale())
                || regularCents != null
                || verdict.discounted();
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

    private String toJson(ExtractedTag tag) {
        try {
            return mapper.writeValueAsString(tag);
        } catch (JacksonException e) {
            log.debug("Could not serialize raw extraction", e);
            return null;
        }
    }
}
