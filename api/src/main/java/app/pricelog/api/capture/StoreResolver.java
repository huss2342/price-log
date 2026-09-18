package app.pricelog.api.capture;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.Store;
import app.pricelog.api.repo.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A store is its chain. Prices barely differ between two Costcos, so naming the
 * location only ever produced "Costco" next to "Costco (unspecified)" for the
 * same shelf.
 */
@Service
public class StoreResolver {

    private final StoreRepository stores;

    public StoreResolver(StoreRepository stores) {
        this.stores = stores;
    }

    /** The one store for a chain, created the first time that chain is seen. */
    @Transactional
    public Store forChain(Chain chain) {
        return stores.findFirstByChainOrderByIdAsc(chain)
                .orElseGet(() -> stores.save(new Store(chain, labelOf(chain), null, null)));
    }

    static String labelOf(Chain chain) {
        return switch (chain) {
            case COSTCO -> "Costco";
            case SAMS_CLUB -> "Sam's Club";
            case ALDI -> "Aldi";
            case WALMART -> "Walmart";
            case OTHER -> "Other";
        };
    }
}
