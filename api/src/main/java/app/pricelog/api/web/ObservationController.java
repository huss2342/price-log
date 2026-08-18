package app.pricelog.api.web;

import app.pricelog.api.capture.ObservationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/observations")
public class ObservationController {

    private final ObservationService observations;

    public ObservationController(ObservationService observations) {
        this.observations = observations;
    }

    /** Captures the model was unsure about. This is the review queue. */
    @GetMapping("/pending")
    public List<ObservationView> pending() {
        return observations.pendingReview().stream().map(ObservationView::of).toList();
    }

    @GetMapping("/recent")
    public List<ObservationView> recent(@RequestParam(defaultValue = "50") int limit) {
        return observations.recent(Math.clamp(limit, 1, 500)).stream()
                .map(ObservationView::of).toList();
    }

    @GetMapping("/{id}")
    public ObservationView get(@PathVariable Long id) {
        return ObservationView.of(observations.get(id));
    }

    @PutMapping("/{id}")
    public ObservationView update(@PathVariable Long id, @Valid @RequestBody ObservationUpdate update) {
        return ObservationView.of(observations.update(id, update));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        observations.delete(id);
    }
}
