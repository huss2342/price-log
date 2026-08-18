package app.pricelog.api.repo;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByChainAndLabel(Chain chain, String label);

    List<Store> findAllByOrderByChainAscLabelAsc();
}
