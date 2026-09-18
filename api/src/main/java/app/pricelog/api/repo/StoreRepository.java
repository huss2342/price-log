package app.pricelog.api.repo;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /** There is one store per chain; see V6__one_store_per_chain.sql. */
    Optional<Store> findFirstByChainOrderByIdAsc(Chain chain);

    List<Store> findAllByOrderByChainAscLabelAsc();
}
