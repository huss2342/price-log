package app.pricelog.api.web;

import app.pricelog.api.capture.ObservationService;
import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.domain.Store;
import app.pricelog.api.domain.TagRule;
import app.pricelog.api.domain.UserProductWatch;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.StoreRepository;
import app.pricelog.api.repo.TagRuleRepository;
import app.pricelog.api.repo.UserProductWatchRepository;
import app.pricelog.api.security.AppUser;
import app.pricelog.api.security.CurrentUser;
import app.pricelog.api.security.UserContext;
import app.pricelog.api.security.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * The user's whole log as one JSON download: stores, observations with their
 * products, tag rules, and the watchlist. Never served anonymously -- this is
 * the account's private data, not the published log.
 */
@RestController
@RequestMapping("/api/export")
@Tag(name = "export", description = "Download the account's data as JSON")
public class ExportController {

    private final UserContext users;
    private final UserRepository userRepository;
    private final StoreRepository stores;
    private final PriceObservationRepository observations;
    private final TagRuleRepository rules;
    private final UserProductWatchRepository watches;
    private final ObservationService observationService;
    private final ObjectMapper mapper;

    public ExportController(UserContext users,
                            UserRepository userRepository,
                            StoreRepository stores,
                            PriceObservationRepository observations,
                            TagRuleRepository rules,
                            UserProductWatchRepository watches,
                            ObservationService observationService,
                            ObjectMapper mapper) {
        this.users = users;
        this.userRepository = userRepository;
        this.stores = stores;
        this.observations = observations;
        this.rules = rules;
        this.watches = watches;
        this.observationService = observationService;
        this.mapper = mapper;
    }

    @Operation(summary = "Download the account's data as JSON")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export() {
        CurrentUser current = users.require();
        Long userId = current.id();
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("No user " + userId));

        List<PriceObservation> rows = observations.findByUserIdOrderByObservedOnDescIdDesc(userId);
        List<ObservationView> views = observationService.views(rows);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("exportedAt", Instant.now().toString());
        body.put("user", Map.of("email", user.getEmail(), "displayName", user.getDisplayName()));
        body.put("stores", stores.findByUserIdOrderByChainAscLabelAsc(userId).stream()
                .map(this::storeOf).toList());
        body.put("observations", views);
        body.put("tagRules", rules.findByUserIdOrderByChainAscPriorityAsc(userId).stream()
                .map(this::ruleOf).toList());
        body.put("watchlist", watches.findByUserId(userId).stream()
                .map(this::watchOf).toList());

        byte[] json;
        try {
            json = mapper.writeValueAsBytes(body);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("Could not serialize the export", e);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("price-log-export.json").build().toString())
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(json);
    }

    private Map<String, Object> storeOf(Store store) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", store.getId());
        view.put("chain", store.getChain());
        view.put("label", store.getLabel());
        view.put("city", store.getCity());
        view.put("state", store.getState());
        view.put("createdAt", store.getCreatedAt() == null ? null : store.getCreatedAt().toString());
        view.put("updatedAt", store.getUpdatedAt() == null ? null : store.getUpdatedAt().toString());
        return view;
    }

    private Map<String, Object> ruleOf(TagRule rule) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", rule.getId());
        view.put("chain", rule.getChain());
        view.put("matchType", rule.getMatchType());
        view.put("pattern", rule.getPattern());
        view.put("signal", rule.getSignal());
        view.put("meaning", rule.getMeaning());
        view.put("advice", rule.getAdvice());
        view.put("priority", rule.getPriority());
        view.put("enabled", rule.isEnabled());
        return view;
    }

    private Map<String, Object> watchOf(UserProductWatch watch) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("productId", watch.getProductId());
        view.put("watched", watch.isWatched());
        view.put("targetPriceCents", watch.getTargetPriceCents());
        return view;
    }
}
