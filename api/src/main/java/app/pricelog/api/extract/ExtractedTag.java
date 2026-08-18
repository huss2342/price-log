package app.pricelog.api.extract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * Raw output of the vision model, before any normalization or rule matching.
 * Every field is nullable because the model is instructed to report absence
 * rather than guess.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedTag(
        String itemName,
        String brand,
        String commodity,
        String category,
        BigDecimal sizeValue,
        String sizeUnit,
        Integer packCount,
        BigDecimal price,
        BigDecimal regularPrice,
        BigDecimal savingsAmount,
        Boolean onSale,
        String saleEndsOn,
        BigDecimal printedUnitPrice,
        String printedUnitPriceUnit,
        String itemNumber,
        String storeChain,
        List<String> markers,
        List<String> attributes,
        String rawText,
        BigDecimal confidence,
        Boolean readable) {

    public List<String> markersOrEmpty() {
        return markers == null ? List.of() : markers;
    }

    public List<String> attributesOrEmpty() {
        return attributes == null ? List.of() : attributes;
    }
}
