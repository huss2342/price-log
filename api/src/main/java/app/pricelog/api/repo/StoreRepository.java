package app.pricelog.api.repo;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /** The user's default store for a chain, created on first capture. */
    Optional<Store> findFirstByUserIdAndChainOrderByIdAsc(Long userId, Chain chain);

    List<Store> findByUserIdOrderByChainAscLabelAsc(Long userId);

    Optional<Store> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndChainAndLabel(Long userId, Chain chain, String label);

    boolean existsByUserIdAndChainAndLabelAndIdNot(Long userId, Chain chain, String label, Long id);
}
