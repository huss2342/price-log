package app.pricelog.api.alerts;

import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.repo.PriceObservationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what an item cost the last time it was seen, at the moment a new
 * sighting is saved. Comparing against the past is the thing you actually want
 * to know in the aisle, so it is not something to opt into.
 */
@Service
public class PriceHistoryService {

    private final PriceObservationRepository observations;

    public PriceHistoryService(PriceObservationRepository observations) {
        this.observations = observations;
    }

    /**
     * Fills in the previous price, its date, and the lowest price seen before
     * now. Call before saving, while "previous" still means previous.
     */
    @Transactional(readOnly = true)
    public void attachHistory(PriceObservation observation) {
        Long productId = observation.getProduct().getId();
        if (productId == null) {
            return;
        }

        List<PriceObservation> history =
                observations.findByProductIdOrderByObservedOnDescIdDesc(productId);
        if (history.isEmpty()) {
            return;
        }

        PriceObservation previous = history.getFirst();
        observation.setPreviousPriceCents(previous.getPriceCents());
        observation.setPreviousObservedOn(previous.getObservedOn());
        observation.setLowestBeforeCents(observations.findLowestPriceCents(productId));
    }
}
