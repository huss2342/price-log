package app.pricelog.api.alerts;

import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.domain.Product;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.ProductRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the watchlist screen: what is being tracked and what looks overdue. */
@Service
public class AlertService {

    private final ProductRepository products;
    private final PriceObservationRepository observations;
    private final SaleCycleService cycles;

    public AlertService(ProductRepository products,
                        PriceObservationRepository observations,
                        SaleCycleService cycles) {
        this.products = products;
        this.observations = observations;
        this.cycles = cycles;
    }

    @Transactional(readOnly = true)
    public List<WatchedItem> watchlist() {
        // Newest first, so the first sighting of each product is the current one.
        Map<Long, PriceObservation> latest = new LinkedHashMap<>();
        for (PriceObservation o : observations.findWatched()) {
            latest.putIfAbsent(o.getProduct().getId(), o);
        }

        List<WatchedItem> items = new ArrayList<>();
        for (var entry : latest.entrySet()) {
            PriceObservation o = entry.getValue();
            Product p = o.getProduct();

            SaleCycle cycle = cycles.forProduct(p.getId());
            Integer best = observations.findLowestPriceCents(p.getId());
            Integer target = p.getTargetPriceCents();

            items.add(new WatchedItem(
                    p.getId(),
                    p.getDisplayName(),
                    p.getBrand(),
                    o.getItemNumber(),
                    target,
                    o.getPriceCents(),
                    best,
                    o.getObservedOn(),
                    ChronoUnit.DAYS.between(o.getObservedOn(), LocalDate.now()),
                    target != null && o.getPriceCents() <= target,
                    cycle,
                    // Only claim something is overdue when the rhythm is real.
                    cycle.confident() && cycle.dueInDays() != null && cycle.dueInDays() <= 0));
        }

        // Overdue first, then the ones seen longest ago.
        items.sort(Comparator
                .comparing(WatchedItem::dueForSale).reversed()
                .thenComparing(Comparator.comparingLong(WatchedItem::daysSinceSeen).reversed()));
        return items;
    }

    @Transactional
    public Product setWatched(Long productId, Boolean watched, Integer targetPriceCents,
                              boolean clearTarget) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new NoSuchElementException("No product " + productId));

        if (watched != null) {
            product.setWatched(watched);
        }
        if (clearTarget) {
            product.setTargetPriceCents(null);
        } else if (targetPriceCents != null) {
            product.setTargetPriceCents(targetPriceCents);
            // Setting a target implies wanting to hear about it.
            product.setWatched(true);
        }
        return products.save(product);
    }
}
