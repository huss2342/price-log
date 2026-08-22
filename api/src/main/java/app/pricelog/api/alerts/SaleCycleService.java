package app.pricelog.api.alerts;

import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.repo.PriceObservationRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estimates how often an item goes on sale, from the sightings already logged.
 *
 * <p>Warehouse-club promotions run on cycles, so the gaps between discounts are
 * informative once there are a few. With fewer than three sales the estimate is
 * reported but flagged as not confident, rather than dressed up as a prediction.
 */
@Service
public class SaleCycleService {

    /** Below this many sales the average gap is barely more than a guess. */
    private static final int CONFIDENT_SALES = 3;

    /** Two sightings of the same promotion should not count as two sales. */
    private static final int SAME_SALE_WINDOW_DAYS = 14;

    private final PriceObservationRepository observations;

    public SaleCycleService(PriceObservationRepository observations) {
        this.observations = observations;
    }

    @Transactional(readOnly = true)
    public SaleCycle forProduct(Long productId) {
        List<PriceObservation> history =
                observations.findByProductIdOrderByObservedOnDescIdDesc(productId);

        // Oldest first, keeping only the start of each distinct sale.
        List<LocalDate> saleStarts = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            PriceObservation o = history.get(i);
            if (!o.isOnSale()) {
                continue;
            }
            LocalDate date = o.getObservedOn();
            if (saleStarts.isEmpty()
                    || ChronoUnit.DAYS.between(saleStarts.getLast(), date) > SAME_SALE_WINDOW_DAYS) {
                saleStarts.add(date);
            }
        }

        if (saleStarts.isEmpty()) {
            return SaleCycle.unknown();
        }

        LocalDate lastSale = saleStarts.getLast();
        long daysSince = ChronoUnit.DAYS.between(lastSale, LocalDate.now());

        if (saleStarts.size() < 2) {
            return new SaleCycle(1, null, lastSale, daysSince, null, false);
        }

        long total = 0;
        for (int i = 1; i < saleStarts.size(); i++) {
            total += ChronoUnit.DAYS.between(saleStarts.get(i - 1), saleStarts.get(i));
        }
        int averageGap = (int) Math.round((double) total / (saleStarts.size() - 1));

        return new SaleCycle(
                saleStarts.size(),
                averageGap,
                lastSale,
                daysSince,
                (int) (averageGap - daysSince),
                saleStarts.size() >= CONFIDENT_SALES);
    }
}
