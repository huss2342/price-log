package app.pricelog.api.web;

import app.pricelog.api.domain.TagRule;
import app.pricelog.api.repo.TagRuleRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.*;

/**
 * The tag conventions are seeded from community knowledge, not from the
 * retailers, so they are editable. Correct them here as you verify them.
 */
@RestController
@RequestMapping("/api/tag-rules")
public class TagRuleController {

    private final TagRuleRepository rules;

    public TagRuleController(TagRuleRepository rules) {
        this.rules = rules;
    }

    @GetMapping
    public List<TagRule> list() {
        return rules.findAllByOrderByChainAscPriorityAsc();
    }

    public record RuleUpdate(String meaning, String advice, Integer priority, Boolean enabled) {
    }

    @PutMapping("/{id}")
    public TagRule update(@PathVariable Long id, @RequestBody RuleUpdate update) {
        TagRule rule = rules.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No tag rule " + id));
        if (update.meaning() != null && !update.meaning().isBlank()) {
            rule.setMeaning(update.meaning());
        }
        if (update.advice() != null) {
            rule.setAdvice(update.advice().isBlank() ? null : update.advice());
        }
        if (update.priority() != null) {
            rule.setPriority(update.priority());
        }
        if (update.enabled() != null) {
            rule.setEnabled(update.enabled());
        }
        return rules.save(rule);
    }
}
