package app.pricelog.api.web;

import app.pricelog.api.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Enum vocabularies, so the UI never hardcodes a list that can drift. */
@RestController
@RequestMapping("/api/meta")
@Tag(name = "meta", description = "Enum vocabularies for the UI")
public class MetaController {

    @Operation(summary = "Enum vocabularies")
    @GetMapping
    public Map<String, List<String>> meta() {
        return Map.of(
                "chains", names(Chain.values()),
                "categories", names(Category.values()),
                "attributes", names(QualityAttribute.values()),
                "saleSignals", names(SaleSignal.values()),
                "baseUnits", names(BaseUnit.values()));
    }

    private List<String> names(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }
}
