package app.pricelog.api.web;

import app.pricelog.api.capture.ObservationService;
import app.pricelog.api.domain.PriceObservation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/observations")
@Tag(name = "observations", description = "Logged prices: browse, correct, delete")
public class ObservationController {

    private final ObservationService observations;

    public ObservationController(ObservationService observations) {
        this.observations = observations;
    }

    /** Captures the model was unsure about. This is the review queue. */
    @Operation(summary = "Captures waiting for confirmation")
    @GetMapping("/pending")
    public List<ObservationView> pending() {
        return observations.views(observations.pendingReview());
    }

    /**
     * The log, newest first. With page and size this is a page
     * ({content, page, size, totalElements, totalPages}); without them it is
     * the whole log as one array, so existing clients keep working.
     */
    @Operation(summary = "The log, whole or paged")
    @GetMapping
    public Object all(@RequestParam(required = false) Integer page,
                      @RequestParam(required = false) Integer size) {
        if (page == null && size == null) {
            return observations.views(observations.all());
        }
        int pageNumber = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null ? 50 : Math.clamp(size, 1, 500);
        Page<PriceObservation> result = observations.page(
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "observedOn", "id")));
        return new ObservationPage(
                observations.views(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public record ObservationPage(List<ObservationView> content, int page, int size,
                                  long totalElements, int totalPages) {
    }

    @Operation(summary = "Recent observations")
    @GetMapping("/recent")
    public List<ObservationView> recent(@RequestParam(defaultValue = "50") int limit) {
        return observations.views(observations.recent(Math.clamp(limit, 1, 500)));
    }

    @Operation(summary = "One logged price")
    @GetMapping("/{id}")
    public ObservationView get(@PathVariable Long id) {
        return single(observations.get(id));
    }

    @Operation(summary = "Correct a logged price")
    @PutMapping("/{id}")
    public ObservationView update(@PathVariable Long id, @Valid @RequestBody ObservationUpdate update) {
        return single(observations.update(id, update));
    }

    @Operation(summary = "Delete a logged price and its photo")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        observations.delete(id);
    }

    private ObservationView single(PriceObservation observation) {
        return observations.views(List.of(observation)).getFirst();
    }
}
