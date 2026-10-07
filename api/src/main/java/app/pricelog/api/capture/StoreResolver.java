package app.pricelog.api.capture;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.Store;
import app.pricelog.api.repo.StoreRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Resolves a user's default store for a chain: the one a capture logs to when
 * no exact store was picked. The first capture for a user and chain creates it;
 * two captures racing that first insert resolve to the same row because the
 * unique index on (user_id, chain, label) makes the loser fail and re-read.
 */
@Service
public class StoreResolver {

    private final StoreRepository stores;
    private final DefaultStoreInserter inserter;

    public StoreResolver(StoreRepository stores, DefaultStoreInserter inserter) {
        this.stores = stores;
        this.inserter = inserter;
    }

    /**
     * The user's default store for a chain, created the first time that user
     * logs that chain. Not transactional itself: the insert runs in its own
     * transaction so a lost race re-reads on a clean connection instead of
     * inside the aborted one.
     */
    public Store forChain(Long userId, Chain chain) {
        return stores.findFirstByUserIdAndChainOrderByIdAsc(userId, chain)
                .orElseGet(() -> {
                    try {
                        return inserter.insert(userId, chain);
                    } catch (DataIntegrityViolationException race) {
                        // Another request created it first; read what won.
                        return stores.findFirstByUserIdAndChainOrderByIdAsc(userId, chain)
                                .orElseThrow(() -> race);
                    }
                });
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
