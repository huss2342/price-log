package app.pricelog.api.repo;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.TagRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRuleRepository extends JpaRepository<TagRule, Long> {

    List<TagRule> findByChainAndEnabledTrueOrderByPriorityAsc(Chain chain);

    List<TagRule> findAllByOrderByChainAscPriorityAsc();
}
