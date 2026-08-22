package app.pricelog.api.web;

import app.pricelog.api.alerts.AlertService;
import app.pricelog.api.alerts.SaleCycle;
import app.pricelog.api.alerts.SaleCycleService;
import app.pricelog.api.alerts.WatchedItem;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AlertController {

    private final AlertService alerts;
    private final SaleCycleService cycles;

    public AlertController(AlertService alerts, SaleCycleService cycles) {
        this.alerts = alerts;
        this.cycles = cycles;
    }

    @GetMapping("/watchlist")
    public List<WatchedItem> watchlist() {
        return alerts.watchlist();
    }

    @GetMapping("/products/{productId}/cycle")
    public SaleCycle cycle(@PathVariable Long productId) {
        return cycles.forProduct(productId);
    }

    /**
     * Every field is a wrapper, never a primitive. Jackson 3 rejects a null for
     * a primitive rather than defaulting it, so an omitted flag would fail the
     * whole request with a 400 instead of meaning "leave it alone".
     *
     * @param clearTarget send true to remove a target price, since a null
     *                    target is indistinguishable from "not sent"
     */
    public record WatchRequest(Boolean watched, Integer targetPriceCents, Boolean clearTarget) {
    }

    @PutMapping("/products/{productId}/watch")
    public ObservationWatchView watch(@PathVariable Long productId, @RequestBody WatchRequest request) {
        var product = alerts.setWatched(
                productId, request.watched(), request.targetPriceCents(),
                Boolean.TRUE.equals(request.clearTarget()));
        return new ObservationWatchView(
                product.getId(), product.isWatched(), product.getTargetPriceCents());
    }

    public record ObservationWatchView(Long productId, boolean watched, Integer targetPriceCents) {
    }
}
