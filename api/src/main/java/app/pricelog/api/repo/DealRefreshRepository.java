package app.pricelog.api.repo;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.DealRefresh;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRefreshRepository extends JpaRepository<DealRefresh, Chain> {
}
