package app.pricelog.api.repo;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.TagRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRuleRepository extends JpaRepository<TagRule, Long> {

    List<TagRule> findByUserIdAndChainAndEnabledTrueOrderByPriorityAsc(Long userId, Chain chain);

    List<TagRule> findByUserIdOrderByChainAscPriorityAsc(Long userId);

    Optional<TagRule> findByIdAndUserId(Long id, Long userId);

    List<TagRule> findByUserId(Long userId);
}
