package app.pricelog.api.web;

import app.pricelog.api.deals.BrowsedDeal;
import app.pricelog.api.deals.DealService;
import app.pricelog.api.deals.DealsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Promotions Costco publishes itself, matched to items already logged.
 * Answers "has anything I buy gone on sale" without being in the warehouse.
 */
@RestController
@RequestMapping("/api/deals")
@Tag(name = "deals", description = "Costco's published promotions, matched to the log")
public class DealsController {

    private final DealService deals;

    public DealsController(DealService deals) {
        this.deals = deals;
    }

    /**
     * @param force re-read the source even if the cached copy is still fresh.
     *              Deprecated: forcing is a side effect, so prefer POST /refresh.
     */
    @Operation(summary = "Promotions matched to logged items")
    @GetMapping
    public DealsView deals(@RequestParam(defaultValue = "false") boolean force) {
        return deals.costcoDeals(force);
    }

    /** Re-read the source now, even if the cached copy is still fresh. */
    @Operation(summary = "Refresh the published promotions now")
    @PostMapping("/refresh")
    public DealsView refresh() {
        return deals.costcoDeals(true);
    }

    /**
     * The full published listing. The summary above only surfaces promotions on
     * items already logged, which is a few out of a couple of hundred.
     *
     * @param q             optional filter on title or item number
     * @param warehouseOnly drop online-only offers
     */
    @Operation(summary = "Browse the full published listing")
    @GetMapping("/published")
    public List<BrowsedDeal> published(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean warehouseOnly) {
        return deals.browse(q, warehouseOnly);
    }
}
