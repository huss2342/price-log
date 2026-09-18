package app.pricelog.api.web;

import app.pricelog.api.domain.Store;
import app.pricelog.api.repo.StoreRepository;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * The chains prices have been logged at. Read-only: a store is its chain and is
 * created the first time a tag from that chain is captured, so there is nothing
 * to name, add or delete.
 */
@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreRepository stores;

    public StoreController(StoreRepository stores) {
        this.stores = stores;
    }

    @GetMapping
    public List<Store> list() {
        return stores.findAllByOrderByChainAscLabelAsc();
    }
}
