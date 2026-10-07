package app.pricelog.api.repo;

import app.pricelog.api.domain.UserProductWatch;
import app.pricelog.api.domain.UserProductWatchId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProductWatchRepository
        extends JpaRepository<UserProductWatch, UserProductWatchId> {

    List<UserProductWatch> findByUserId(Long userId);

    List<UserProductWatch> findByUserIdAndProductIdIn(Long userId, Collection<Long> productIds);

    Optional<UserProductWatch> findByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserId(Long userId);
}
