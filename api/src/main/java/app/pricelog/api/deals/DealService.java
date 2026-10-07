package app.pricelog.api.deals;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.DealRefresh;
import app.pricelog.api.domain.DealSighting;
import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.repo.DealRefreshRepository;
import app.pricelog.api.repo.DealRepository;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.security.UserContext;
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
 * deals screen refreshes it if the cache has aged out, which holds Costco to at
 * most one read a day. Browsing the stored listing never triggers a read at
 * all -- the rows are already there, and outlive the container.
 */
@Service
public class DealService {

    private static final Logger log = LoggerFactory.getLogger(DealService.class);

    private final CostcoDealsFetcher fetcher;
    private final DealRepository deals;
    private final DealRefreshRepository refreshes;
    private final PriceObservationRepository observations;
    private final UserContext users;
    private final Duration maxAge;

    public DealService(CostcoDealsFetcher fetcher,
                       DealRepository deals,
                       DealRefreshRepository refreshes,
                       PriceObservationRepository observations,
                       UserContext users,
                       @Value("${pricelog.deals.max-age-hours:24}") long maxAgeHours) {
        this.fetcher = fetcher;
        this.deals = deals;
        this.refreshes = refreshes;
        this.observations = observations;
        this.users = users;
        this.maxAge = Duration.ofHours(maxAgeHours);
    }

    @Transactional
    public DealsView costcoDeals(boolean force) {
        DealRefresh state = refreshes.findById(Chain.COSTCO).orElseGet(() -> new DealRefresh(Chain.COSTCO));

        if (force || isStale(state)) {
            refresh(state);
        }

        return new DealsView(
                match(users.userId()),
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
                    row.setSalePriceCents(deal.salePriceCents());
                    row.setDiscountCents(deal.discountCents());
                    row.setInWarehouse(deal.inWarehouse());
                    row.setValidUntil(deal.validUntil());
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

    /**
     * The whole active listing, not just what matched. Costco publishes a couple
     * of hundred offers and the deals screen only ever surfaces the few that
     * touch something already logged, so without this the rest are invisible
     * even though they are already stored.
     *
     * @param query optional case-insensitive filter on the title or item number
     * @param warehouseOnly drop online-only offers, which cannot be picked up
     *                      on a warehouse trip
     */
    @Transactional(readOnly = true)
    public List<BrowsedDeal> browse(String query, boolean warehouseOnly) {
        Set<String> logged = new HashSet<>(observations.findItemNumbersForChain(users.userId(), Chain.COSTCO));
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return deals.findByChainAndActiveTrue(Chain.COSTCO).stream()
                .filter(d -> !warehouseOnly || d.isInWarehouse())
                .filter(d -> needle.isEmpty()
                        || d.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                        || (d.getItemNumber() != null && d.getItemNumber().contains(needle)))
                .map(d -> new BrowsedDeal(
                        d.getItemNumber(),
                        d.getTitle(),
                        d.getSalePriceCents(),
                        d.getDiscountCents(),
                        d.isInWarehouse(),
                        d.getItemNumber() != null && logged.contains(d.getItemNumber()),
                        d.getFirstSeenOn(),
                        d.getLastSeenOn()))
                // Items you already buy first, then the biggest savings.
                .sorted(Comparator
                        .comparing(BrowsedDeal::logged).reversed()
                        .thenComparing(Comparator.comparing(
                                (BrowsedDeal b) -> savingOf(b)).reversed())
                        .thenComparing(BrowsedDeal::title))
                .toList();
    }

    /** Ranks on whatever figure the listing gave: the discount, else the price. */
    private static int savingOf(BrowsedDeal deal) {
        if (deal.discountCents() != null) {
            return deal.discountCents();
        }
        return 0;
    }

    /** Joins active promotions to the current user's logged items on the retailer's item number. */
    private List<DealMatch> match(Long userId) {
        List<String> itemNumbers = observations.findItemNumbersForChain(userId, Chain.COSTCO);
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
                userId, org.springframework.data.domain.PageRequest.of(0, 500))) {
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
                matches.add(toMatch(userId, deal, observation));
            }
        }

        // Ending soonest first, since those are the ones to act on; then the
        // biggest savings.
        matches.sort(Comparator
                .comparing(DealMatch::validUntil, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Comparator.comparing(
                        (DealMatch m) -> m.discountCents() == null ? 0 : m.discountCents()).reversed()));
        return matches;
    }

    private DealMatch toMatch(Long userId, DealSighting deal, PriceObservation observation) {
        Long productId = observation.getProduct().getId();
        Integer regular = regularPriceCents(userId, productId);

        // Costco's own figures are the truth when it prints them. It usually
        // prints only one of price and saving, and the other is only as good as
        // the regular price last photographed.
        Integer dealPrice = deal.getSalePriceCents();
        Integer discount = deal.getDiscountCents();
        boolean estimated = false;
        if (dealPrice == null && discount != null && regular != null) {
            dealPrice = Math.max(0, regular - discount);
            estimated = true;
        }
        if (discount == null && dealPrice != null && regular != null && regular > dealPrice) {
            discount = regular - dealPrice;
            estimated = true;
        }

        return new DealMatch(
                productId,
                observation.getProduct().getDisplayName(),
                observation.getProduct().getBrand(),
                deal.getItemNumber(),
                deal.getTitle(),
                regular,
                dealPrice,
                discount,
                estimated,
                deal.isInWarehouse(),
                deal.getValidUntil(),
                observation.getObservedOn(),
                ChronoUnit.DAYS.between(observation.getObservedOn(), LocalDate.now()));
    }

    /**
     * The price without any promotion: the regular price a sale tag printed
     * beside its own, or the newest sighting that was not on sale at all.
     */
    private Integer regularPriceCents(Long userId, Long productId) {
        for (PriceObservation o : observations.findByUserIdAndProductIdOrderByObservedOnDescIdDesc(userId, productId)) {
            if (o.getRegularPriceCents() != null && o.getRegularPriceCents() > o.getPriceCents()) {
                return o.getRegularPriceCents();
            }
            if (!o.isOnSale()) {
                return o.getPriceCents();
            }
        }
        return null;
    }

    private String fingerprint(String itemNumber, String title) {
        String slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return truncate("COSTCO|" + itemNumber + "|" + slug, 255);
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
