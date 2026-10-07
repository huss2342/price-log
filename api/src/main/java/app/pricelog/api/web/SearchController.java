package app.pricelog.api.web;

import app.pricelog.api.domain.Category;
import app.pricelog.api.query.CompareGroup;
import app.pricelog.api.query.PriceQueryService;
import app.pricelog.api.query.StoreOffer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "search", description = "Find items and compare prices across stores")
public class SearchController {

    private final PriceQueryService queries;

    public SearchController(PriceQueryService queries) {
        this.queries = queries;
    }

    /** "eggs" -> every comparison group matching, each with per-store prices. */
    @Operation(summary = "Search logged items")
    @GetMapping("/search")
    public List<CompareGroup> search(@RequestParam String q) {
        return queries.search(q);
    }

    @Operation(summary = "Browse one category")
    @GetMapping("/categories/{category}")
    public List<CompareGroup> byCategory(@PathVariable Category category) {
        return queries.byCategory(category);
    }

    @Operation(summary = "One comparison group")
    @GetMapping("/groups/{comparisonKey}")
    public CompareGroup group(@PathVariable String comparisonKey) {
        return queries.byComparisonKey(comparisonKey)
                .orElseThrow(() -> new NoSuchElementException("No group " + comparisonKey));
    }

    /** Every price ever logged for one product, newest first. */
    @Operation(summary = "Price history of one product")
    @GetMapping("/products/{productId}/history")
    public List<StoreOffer> history(@PathVariable Long productId) {
        return queries.history(productId);
    }
}
