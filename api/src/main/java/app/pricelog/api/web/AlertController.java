package app.pricelog.api.web;

import app.pricelog.api.alerts.AlertService;
import app.pricelog.api.alerts.SaleCycle;
import app.pricelog.api.alerts.SaleCycleService;
import app.pricelog.api.alerts.WatchedItem;
import app.pricelog.api.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "alerts", description = "Watchlist and sale-cycle estimates")
public class AlertController {

    private final AlertService alerts;
    private final SaleCycleService cycles;
    private final UserContext users;

    public AlertController(AlertService alerts, SaleCycleService cycles, UserContext users) {
        this.alerts = alerts;
        this.cycles = cycles;
        this.users = users;
    }

    @Operation(summary = "Items being tracked")
    @GetMapping("/watchlist")
    public List<WatchedItem> watchlist() {
        return alerts.watchlist();
    }

    @Operation(summary = "How often an item goes on sale")
    @GetMapping("/products/{productId}/cycle")
    public SaleCycle cycle(@PathVariable Long productId) {
        return cycles.forProduct(users.userId(), productId);
    }

    /**
     * Every field is a wrapper, never a primitive. Jackson 3 rejects a null for
     * a primitive rather than defaulting it, so an omitted flag would fail the
     * whole request with a 400 instead of meaning "leave it alone".
     *
     * @param clearTarget send true to remove a target price, since a null
     *                    target is indistinguishable from "not sent"
     */
    public record WatchRequest(Boolean watched,
                               @Min(value = 0, message = "must be 0 or more") Integer targetPriceCents,
                               Boolean clearTarget) {
    }

    @Operation(summary = "Watch a product or set its target price")
    @PutMapping("/products/{productId}/watch")
    public ObservationWatchView watch(@PathVariable Long productId,
                                      @Valid @RequestBody(required = false) WatchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }
        AlertService.WatchResult result = alerts.setWatched(
                productId, request.watched(), request.targetPriceCents(),
                Boolean.TRUE.equals(request.clearTarget()));
        return new ObservationWatchView(result.productId(), result.watched(), result.targetPriceCents());
    }

    public record ObservationWatchView(Long productId, boolean watched, Integer targetPriceCents) {
    }
}
