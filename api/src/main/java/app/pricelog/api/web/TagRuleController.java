package app.pricelog.api.web;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.MatchType;
import app.pricelog.api.domain.SaleSignal;
import app.pricelog.api.domain.TagRule;
import app.pricelog.api.repo.TagRuleRepository;
import app.pricelog.api.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * The tag conventions are seeded from community knowledge, not from the
 * retailers, so they are editable. Correct them here as you verify them.
 * Every account keeps its own set, seeded from the owner's at registration.
 */
@RestController
@RequestMapping("/api/tag-rules")
@Tag(name = "tag-rules", description = "Price-tag conventions, editable per account")
public class TagRuleController {

    private final TagRuleRepository rules;
    private final UserContext users;

    public TagRuleController(TagRuleRepository rules, UserContext users) {
        this.rules = rules;
        this.users = users;
    }

    @Operation(summary = "The user's tag rules")
    @GetMapping
    public List<TagRule> list() {
        return rules.findByUserIdOrderByChainAscPriorityAsc(users.userId());
    }

    public record RuleCreate(String chain, String matchType,
                             @NotBlank(message = "must not be blank") String pattern,
                             String signal,
                             @NotBlank(message = "must not be blank") String meaning,
                             String advice,
                             @Min(value = 0, message = "must be 0 or more") Integer priority) {
    }

    public record RuleUpdate(String meaning, String advice,
                             @Min(value = 0, message = "must be 0 or more") Integer priority,
                             Boolean enabled) {
    }

    @Operation(summary = "Add a tag rule")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TagRule create(@Valid @RequestBody RuleCreate request) {
        TagRule rule = new TagRule();
        rule.setUserId(users.userId());
        rule.setChain(parseEnum(Chain.class, request.chain(), "chain"));
        rule.setMatchType(parseEnum(MatchType.class, request.matchType(), "matchType"));
        rule.setPattern(request.pattern().trim());
        rule.setSignal(parseEnum(SaleSignal.class, request.signal(), "signal"));
        rule.setMeaning(request.meaning().trim());
        rule.setAdvice(request.advice() == null || request.advice().isBlank()
                ? null : request.advice().trim());
        rule.setPriority(request.priority() == null ? 100 : request.priority());
        rule.setEnabled(true);
        return rules.save(rule);
    }

    @Operation(summary = "Edit a tag rule")
    @PutMapping("/{id}")
    @Transactional
    public TagRule update(@PathVariable Long id, @Valid @RequestBody RuleUpdate update) {
        TagRule rule = rules.findByIdAndUserId(id, users.userId())
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

    @Operation(summary = "Delete a tag rule")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        TagRule rule = rules.findByIdAndUserId(id, users.userId())
                .orElseThrow(() -> new NoSuchElementException("No tag rule " + id));
        rules.delete(rule);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new UnprocessableException(field + " is required.");
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnprocessableException("Unknown " + field + ": " + raw.trim() + ".");
        }
    }
}
