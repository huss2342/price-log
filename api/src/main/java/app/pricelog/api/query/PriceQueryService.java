package app.pricelog.api.query;

import app.pricelog.api.domain.*;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.ProductRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers the shopping questions: what is this item worth, and where is it
 * cheapest. Comparison always happens on normalized unit price, falling back to
 * absolute price only when no size could be determined.
 */
@Service
public class PriceQueryService {

    /** Prices older than this are shown, but flagged as possibly out of date. */
    private static final int STALE_AFTER_DAYS = 60;

    private final ProductRepository products;
    private final PriceObservationRepository observations;

    public PriceQueryService(ProductRepository products, PriceObservationRepository observations) {
        this.products = products;
        this.observations = observations;
    }

    @Transactional(readOnly = true)
    public List<CompareGroup> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return toGroups(products.search(query.trim()));
    }

    @Transactional(readOnly = true)
    public List<CompareGroup> byCategory(Category category) {
        return toGroups(products.findByCategoryOrderByDisplayNameAsc(category));
    }

    @Transactional(readOnly = true)
    public Optional<CompareGroup> byComparisonKey(String comparisonKey) {
        List<Product> matches = products.findByComparisonKey(comparisonKey);
        return matches.isEmpty() ? Optional.empty() : Optional.of(buildGroup(matches));
    }

    /** Every observation for one product, newest first, for the price history chart. */
    @Transactional(readOnly = true)
    public List<StoreOffer> history(Long productId) {
        List<PriceObservation> rows = observations.findForProducts(List.of(productId));
        BigDecimal best = cheapestUnitPrice(rows);
        return rows.stream().map(o -> toOffer(o, best)).toList();
    }

    /**
     * Groups the given products by comparison key, then resolves each group to
     * one offer per store: the newest observation for that product and store.
     */
    private List<CompareGroup> toGroups(List<Product> matches) {
        if (matches.isEmpty()) {
            return List.of();
        }
        Map<String, List<Product>> byKey = matches.stream()
                .collect(Collectors.groupingBy(Product::getComparisonKey, LinkedHashMap::new, Collectors.toList()));

        return byKey.values().stream()
                .map(this::buildGroup)
                .filter(group -> !group.offers().isEmpty())
                .sorted(Comparator.comparing(CompareGroup::label))
                .toList();
    }

    private CompareGroup buildGroup(List<Product> groupProducts) {
        Map<Long, Product> productsById = groupProducts.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<PriceObservation> rows = observations.findForProducts(productsById.keySet());
        List<PriceObservation> latest = latestPerProductAndStore(rows);
        BigDecimal best = cheapestUnitPrice(latest);

        List<StoreOffer> offers = latest.stream()
                .map(o -> toOffer(o, best))
                .sorted(offerOrder())
                .toList();

        Product representative = groupProducts.getFirst();
        return new CompareGroup(
                representative.getComparisonKey(),
                representative.getDisplayName(),
                representative.getCategory(),
                representative.getAttributes(),
                representative.getBaseUnit(),
                offers);
    }

    /**
     * One row per product-and-store pair. The repository already returns newest
     * first, so the first sighting of each pair wins.
     */
    private List<PriceObservation> latestPerProductAndStore(List<PriceObservation> rows) {
        Map<String, PriceObservation> newest = new LinkedHashMap<>();
        for (PriceObservation row : rows) {
            String key = row.getProduct().getId() + ":" + row.getStore().getId();
            newest.putIfAbsent(key, row);
        }
        return List.copyOf(newest.values());
    }

    private BigDecimal cheapestUnitPrice(List<PriceObservation> rows) {
        return rows.stream()
                .map(PriceObservation::getUnitPriceCents)
                .filter(Objects::nonNull)
                .filter(value -> value.signum() > 0)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private StoreOffer toOffer(PriceObservation o, BigDecimal bestUnitPrice) {
        long daysAgo = ChronoUnit.DAYS.between(o.getObservedOn(), LocalDate.now());
        BigDecimal percentAboveBest = null;
        if (bestUnitPrice != null && o.getUnitPriceCents() != null && bestUnitPrice.signum() > 0) {
            percentAboveBest = o.getUnitPriceCents()
                    .subtract(bestUnitPrice)
                    .divide(bestUnitPrice, new MathContext(8, RoundingMode.HALF_UP))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }

        return new StoreOffer(
                o.getId(),
                o.getStore().getId(),
                o.getStore().getChain(),
                o.getStore().getLabel(),
                o.getProduct().getId(),
                o.getProduct().getDisplayName(),
                o.getProduct().getBrand(),
                o.getPriceCents(),
                o.getRegularPriceCents(),
                o.isOnSale(),
                o.getSaleSignal(),
                o.isDiscontinued(),
                o.getUnitPriceCents(),
                o.getBaseUnit(),
                o.getObservedOn(),
                daysAgo,
                daysAgo > STALE_AFTER_DAYS,
                percentAboveBest);
    }

    /** Cheapest per unit first; offers with no unit price sort last, by absolute price. */
    private Comparator<StoreOffer> offerOrder() {
        return Comparator
                .comparing(StoreOffer::unitPriceCents, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(StoreOffer::priceCents);
    }
}
