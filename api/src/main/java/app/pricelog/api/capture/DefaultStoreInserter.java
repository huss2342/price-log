package app.pricelog.api.capture;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.Store;
import app.pricelog.api.repo.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The default-store insert, isolated in its own transaction. On PostgreSQL a
 * failed INSERT aborts the whole transaction, so the unique-violation from a
 * lost first-capture race must happen somewhere the re-read does not depend on.
 */
@Service
public class DefaultStoreInserter {

    private final StoreRepository stores;

    public DefaultStoreInserter(StoreRepository stores) {
        this.stores = stores;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Store insert(Long userId, Chain chain) {
        // saveAndFlush so the unique index is checked now, inside this
        // transaction, rather than at some later commit.
        return stores.saveAndFlush(
                new Store(userId, chain, StoreResolver.labelOf(chain), null, null));
    }
}
