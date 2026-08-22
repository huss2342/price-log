package app.pricelog.api.repo;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.DealSighting;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<DealSighting, Long> {

    Optional<DealSighting> findByFingerprint(String fingerprint);

    List<DealSighting> findByChainAndActiveTrue(Chain chain);

    List<DealSighting> findByActiveTrueAndItemNumberIn(Collection<String> itemNumbers);
}
