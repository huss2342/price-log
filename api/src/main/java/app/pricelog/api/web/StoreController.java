package app.pricelog.api.web;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.Store;
import app.pricelog.api.repo.PriceObservationRepository;
import app.pricelog.api.repo.StoreRepository;
import app.pricelog.api.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * The places prices have been logged. Stores belong to the signed-in user:
 * the same chain can be "Costco" for one person and "Costco Tustin" for
 * another, and neither sees the other's.
 */
@RestController
@RequestMapping("/api/stores")
@Tag(name = "stores", description = "The user's stores: list, add, rename, delete")
public class StoreController {

    private final StoreRepository stores;
    private final PriceObservationRepository observations;
    private final UserContext users;

    public StoreController(StoreRepository stores,
                           PriceObservationRepository observations,
                           UserContext users) {
        this.stores = stores;
        this.observations = observations;
        this.users = users;
    }

    public record StoreView(Long id, Chain chain, String label, String city, String state,
                            Instant createdAt, Instant updatedAt, long observationCount) {
    }

    public record StoreCreate(String chain, @NotBlank(message = "must not be blank") String label,
                              String city, String state) {
    }

    public record StoreRename(String label, String city, String state) {
    }

    @Operation(summary = "The user's stores, with how many prices each holds")
    @GetMapping
    @Transactional(readOnly = true)
    public List<StoreView> list() {
        Long userId = users.userId();
        return stores.findByUserIdOrderByChainAscLabelAsc(userId).stream()
                .map(s -> viewOf(s, observations.countByStoreIdAndUserId(s.getId(), userId)))
                .toList();
    }

    @Operation(summary = "Add a store")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public StoreView create(@Valid @RequestBody StoreCreate request) {
        Long userId = users.userId();
        Chain chain = parseChain(request.chain());
        String label = request.label().trim();
        if (stores.existsByUserIdAndChainAndLabel(userId, chain, label)) {
            throw new IllegalStateException(
                    "You already have a store called \"" + label + "\" for " + chain + ".");
        }
        try {
            Store saved = stores.saveAndFlush(
                    new Store(userId, chain, label, blankToNull(request.city()), blankToNull(request.state())));
            return viewOf(saved, 0);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with another create for the same (user, chain, label).
            throw new IllegalStateException(
                    "You already have a store called \"" + label + "\" for " + chain + ".", e);
        }
    }

    @Operation(summary = "Rename a store")
    @PutMapping("/{id}")
    @Transactional
    public StoreView rename(@PathVariable Long id, @RequestBody StoreRename request) {
        Long userId = users.userId();
        Store store = stores.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("No store " + id));

        if (request.label() != null) {
            String label = request.label().trim();
            if (label.isEmpty()) {
                throw new UnprocessableException("Label must not be blank.");
            }
            if (stores.existsByUserIdAndChainAndLabelAndIdNot(
                    userId, store.getChain(), label, store.getId())) {
                throw new IllegalStateException(
                        "You already have a store called \"" + label + "\" for " + store.getChain() + ".");
            }
            store.setLabel(label);
        }
        if (request.city() != null) {
            store.setCity(blankToNull(request.city()));
        }
        if (request.state() != null) {
            store.setState(blankToNull(request.state()));
        }
        try {
            store = stores.saveAndFlush(store);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "That name collides with another of your stores for " + store.getChain() + ".", e);
        }
        return viewOf(store, observations.countByStoreIdAndUserId(store.getId(), userId));
    }

    @Operation(summary = "Delete a store with no logged prices")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        Long userId = users.userId();
        Store store = stores.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("No store " + id));

        long count = observations.countByStoreIdAndUserId(id, userId);
        if (count > 0) {
            throw new IllegalStateException(
                    "Store has " + count + " logged prices. Move or delete them first.");
        }
        stores.delete(store);
    }

    private StoreView viewOf(Store store, long observationCount) {
        return new StoreView(store.getId(), store.getChain(), store.getLabel(), store.getCity(),
                store.getState(), store.getCreatedAt(), store.getUpdatedAt(), observationCount);
    }

    private Chain parseChain(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UnprocessableException("Chain is required.");
        }
        try {
            return Chain.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnprocessableException("Unknown chain: " + raw.trim() + ".");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
