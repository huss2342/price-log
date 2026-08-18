package app.pricelog.api.web;

import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.Store;
import app.pricelog.api.repo.StoreRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreRepository stores;

    public StoreController(StoreRepository stores) {
        this.stores = stores;
    }

    public record StoreRequest(
            @NotNull Chain chain,
            @NotBlank String label,
            String city,
            String state) {
    }

    @GetMapping
    public List<Store> list() {
        return stores.findAllByOrderByChainAscLabelAsc();
    }

    @PostMapping
    public Store create(@Valid @RequestBody StoreRequest request) {
        return stores.findByChainAndLabel(request.chain(), request.label())
                .orElseGet(() -> stores.save(new Store(
                        request.chain(), request.label(), request.city(), request.state())));
    }

    @PutMapping("/{id}")
    public Store update(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        Store store = stores.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No store " + id));
        store.setChain(request.chain());
        store.setLabel(request.label());
        store.setCity(request.city());
        store.setState(request.state());
        return stores.save(store);
    }
}
