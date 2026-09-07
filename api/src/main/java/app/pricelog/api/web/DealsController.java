package app.pricelog.api.web;

import app.pricelog.api.deals.BrowsedDeal;
import app.pricelog.api.deals.DealService;
import app.pricelog.api.deals.DealsView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Promotions Costco publishes itself, matched to items already logged.
 * Answers "has anything I buy gone on sale" without being in the warehouse.
 */
@RestController
@RequestMapping("/api/deals")
public class DealsController {

    private final DealService deals;

    public DealsController(DealService deals) {
        this.deals = deals;
    }

    /** @param force re-read the source even if the cached copy is still fresh */
    @GetMapping
    public DealsView deals(@RequestParam(defaultValue = "false") boolean force) {
        return deals.costcoDeals(force);
    }

    /**
     * The full published listing. The summary above only surfaces promotions on
     * items already logged, which is a few out of a couple of hundred.
     *
     * @param q             optional filter on title or item number
     * @param warehouseOnly drop online-only offers
     */
    @GetMapping("/published")
    public List<BrowsedDeal> published(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean warehouseOnly) {
        return deals.browse(q, warehouseOnly);
    }
}
