package app.pricelog.api.deals;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.DealRefresh;
import app.pricelog.api.domain.DealSighting;
import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.repo.DealRefreshRepository;
import app.pricelog.api.repo.DealRepository;
import app.pricelog.api.repo.PriceObservationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a cached copy of Costco's published promotions and matches them against
 * items already photographed.
 *
 * <p>The refresh is lazy rather than scheduled, because the container scales to
 * zero: a timer would simply never fire while the app is asleep. Opening the
 * deals screen refreshes it if the cache has aged out, which also keeps the
 * number of requests to Costco to a couple a day.
 */
@Service
public class DealService {

    private static final Logger log = LoggerFactory.getLogger(DealService.class);

    private final CostcoDealsFetcher fetcher;
    private final DealRepository deals;
    private final DealRefreshRepository refreshes;
    private final PriceObservationRepository observations;
    private final Duration maxAge;

    public DealService(CostcoDealsFetcher fetcher,
                       DealRepository deals,
                       DealRefreshRepository refreshes,
                       PriceObservationRepository observations,
                       @Value("${pricelog.deals.max-age-hours:12}") long maxAgeHours) {
        this.fetcher = fetcher;
        this.deals = deals;
        this.refreshes = refreshes;
        this.observations = observations;
        this.maxAge = Duration.ofHours(maxAgeHours);
    }

    @Transactional
    public DealsView costcoDeals(boolean force) {
        DealRefresh state = refreshes.findById(Chain.COSTCO).orElseGet(() -> new DealRefresh(Chain.COSTCO));

        if (force || isStale(state)) {
            refresh(state);
        }

        return new DealsView(
                match(),
                deals.findByChainAndActiveTrue(Chain.COSTCO).size(),
                state.getLastSuccessAt(),
                state.getLastError());
    }

    private boolean isStale(DealRefresh state) {
        Instant last = state.getLastSuccessAt();
        return last == null || last.isBefore(Instant.now().minus(maxAge));
    }

    /**
     * Reads the source and reconciles it with what is stored. A failure is
     * recorded and swallowed: stale deals are far better than an error screen.
     */
    private void refresh(DealRefresh state) {
        state.setLastAttemptAt(Instant.now());
        try {
            List<PublishedDeal> published = fetcher.fetch();
            if (published.isEmpty()) {
                throw new IllegalStateException(
                        "No deals could be read from the page. Costco has probably changed its layout.");
            }

            LocalDate today = LocalDate.now();
            Set<String> seen = new HashSet<>();

            for (PublishedDeal deal : published) {
                // An offer covering several item numbers is stored once per
                // number, so any of them can match a logged item.
                for (String itemNumber : deal.itemNumbers()) {
                    String fingerprint = fingerprint(itemNumber, deal.title());
                    seen.add(fingerprint);

                    DealSighting row = deals.findByFingerprint(fingerprint).orElseGet(() -> {
                        DealSighting fresh = new DealSighting();
                        fresh.setChain(Chain.COSTCO);
                        fresh.setFingerprint(fingerprint);
                        fresh.setFirstSeenOn(today);
                        return fresh;
                    });

                    row.setItemNumber(itemNumber);
                    row.setTitle(truncate(deal.title(), 512));
                    row.setDiscountCents(deal.discountCents());
                    row.setInWarehouse(deal.inWarehouse());
                    row.setLastSeenOn(today);
                    row.setActive(true);
                    deals.save(row);
                }
            }

            // Anything not in this reading has ended.
            for (DealSighting existing : deals.findByChainAndActiveTrue(Chain.COSTCO)) {
                if (!seen.contains(existing.getFingerprint())) {
                    existing.setActive(false);
                    deals.save(existing);
                }
            }

            state.setLastSuccessAt(Instant.now());
            state.setDealsFound(seen.size());
            state.setLastError(null);
            log.info("Refreshed Costco deals: {} offers", seen.size());

        } catch (RuntimeException e) {
            log.warn("Could not refresh Costco deals: {}", e.toString());
            state.setLastError(truncate(e.getMessage() == null ? e.toString() : e.getMessage(), 512));
        }
        refreshes.save(state);
    }

    /** Joins active promotions to logged items on the retailer's item number. */
    private List<DealMatch> match() {
        List<String> itemNumbers = observations.findItemNumbersForChain(Chain.COSTCO);
        if (itemNumbers.isEmpty()) {
            return List.of();
        }

        Map<String, List<DealSighting>> byItem = new HashMap<>();
        for (DealSighting deal : deals.findByActiveTrueAndItemNumberIn(itemNumbers)) {
            byItem.computeIfAbsent(deal.getItemNumber(), k -> new ArrayList<>()).add(deal);
        }
        if (byItem.isEmpty()) {
            return List.of();
        }

        // The newest sighting of each item number is the price to compare against.
        Map<String, PriceObservation> latest = new HashMap<>();
        for (PriceObservation o : observations.findRecent(
                org.springframework.data.domain.PageRequest.of(0, 500))) {
            if (o.getItemNumber() != null) {
                latest.putIfAbsent(o.getItemNumber(), o);
            }
        }

        List<DealMatch> matches = new ArrayList<>();
        for (var entry : byItem.entrySet()) {
            PriceObservation observation = latest.get(entry.getKey());
            if (observation == null) {
                continue;
            }
            for (DealSighting deal : entry.getValue()) {
                matches.add(toMatch(deal, observation));
            }
        }

        // Watched items first, then the biggest discounts.
        matches.sort(Comparator
                .comparing(DealMatch::watched).reversed()
                .thenComparing(Comparator.comparing(
                        (DealMatch m) -> m.discountCents() == null ? 0 : m.discountCents()).reversed()));
        return matches;
    }

    private DealMatch toMatch(DealSighting deal, PriceObservation observation) {
        int lastPrice = observation.getPriceCents();
        Integer implied = deal.getDiscountCents() == null
                ? null
                : Math.max(0, lastPrice - deal.getDiscountCents());

        return new DealMatch(
                observation.getProduct().getId(),
                observation.getProduct().getDisplayName(),
                observation.getProduct().getBrand(),
                deal.getItemNumber(),
                deal.getTitle(),
                deal.getDiscountCents(),
                deal.isInWarehouse(),
                observation.getProduct().isWatched(),
                lastPrice,
                implied,
                observation.getObservedOn(),
                ChronoUnit.DAYS.between(observation.getObservedOn(), LocalDate.now()));
    }

    private String fingerprint(String itemNumber, String title) {
        String slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return truncate("COSTCO|" + itemNumber + "|" + slug, 255);
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
