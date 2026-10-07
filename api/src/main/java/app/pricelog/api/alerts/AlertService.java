package app.pricelog.api.alerts;

import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.domain.Product;
import app.pricelog.api.domain.UserProductWatch;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.ProductRepository;
import app.pricelog.api.repo.UserProductWatchRepository;
import app.pricelog.api.security.UserContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the watchlist screen: what is being tracked and what looks overdue. */
@Service
public class AlertService {

    private final ProductRepository products;
    private final PriceObservationRepository observations;
    private final UserProductWatchRepository watches;
    private final SaleCycleService cycles;
    private final UserContext users;

    public AlertService(ProductRepository products,
                        PriceObservationRepository observations,
                        UserProductWatchRepository watches,
                        SaleCycleService cycles,
                        UserContext users) {
        this.products = products;
        this.observations = observations;
        this.watches = watches;
        this.cycles = cycles;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<WatchedItem> watchlist() {
        Long userId = users.userId();

        // Newest first, so the first sighting of each product is the current one.
        Map<Long, PriceObservation> latest = new LinkedHashMap<>();
        for (PriceObservation o : observations.findWatched(userId)) {
            latest.putIfAbsent(o.getProduct().getId(), o);
        }

        Map<Long, UserProductWatch> watchByProduct = watches.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserProductWatch::getProductId, Function.identity()));

        List<WatchedItem> items = new ArrayList<>();
        for (var entry : latest.entrySet()) {
            PriceObservation o = entry.getValue();
            Product p = o.getProduct();

            SaleCycle cycle = cycles.forProduct(userId, p.getId());
            Integer best = observations.findLowestPriceCents(userId, p.getId());
            Integer target = watchByProduct.get(p.getId()) == null
                    ? null : watchByProduct.get(p.getId()).getTargetPriceCents();

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

    /** The outcome of a watch change, for the response body. */
    public record WatchResult(Long productId, boolean watched, Integer targetPriceCents) {
    }

    @Transactional
    public WatchResult setWatched(Long productId, Boolean watched, Integer targetPriceCents,
                                  boolean clearTarget) {
        Long userId = users.userId();
        products.findById(productId)
                .orElseThrow(() -> new NoSuchElementException("No product " + productId));

        UserProductWatch watch = watches.findByUserIdAndProductId(userId, productId)
                .orElseGet(() -> new UserProductWatch(userId, productId));

        if (watched != null) {
            watch.setWatched(watched);
        }
        if (clearTarget) {
            watch.setTargetPriceCents(null);
        } else if (targetPriceCents != null) {
            watch.setTargetPriceCents(targetPriceCents);
            // Setting a target implies wanting to hear about it.
            watch.setWatched(true);
        }
        watch = watches.save(watch);
        return new WatchResult(productId, watch.isWatched(), watch.getTargetPriceCents());
    }
}
