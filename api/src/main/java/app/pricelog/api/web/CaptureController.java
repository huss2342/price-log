package app.pricelog.api.web;

import app.pricelog.api.capture.CaptureResult;
import app.pricelog.api.capture.CaptureService;
import app.pricelog.api.capture.ObservationService;
import app.pricelog.api.domain.Chain;
import app.pricelog.api.domain.PriceObservation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/captures")
@Tag(name = "captures", description = "Photo a price tag, get back a logged price")
public class CaptureController {

    private final CaptureService captureService;
    private final ObservationService observations;

    public CaptureController(CaptureService captureService, ObservationService observations) {
        this.captureService = captureService;
        this.observations = observations;
    }

    /**
     * The whole app in one call: upload a tag photo, get back a saved,
     * fully-interpreted observation ready for confirmation.
     */
    /**
     * @param chain   where the photo was taken; omitted, the tag's design decides
     * @param storeId an exact store of yours to log at; wins over the chain
     */
    @Operation(summary = "Capture a price tag from a photo")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ObservationView capture(
            @RequestPart("photo") MultipartFile photo,
            @RequestParam(required = false) Chain chain,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate observedOn) {

        if (photo.isEmpty()) {
            throw new IllegalArgumentException("Photo is empty.");
        }

        byte[] bytes;
        try {
            bytes = photo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded photo", e);
        }

        // getContentType() is null when the client sends no content type; the
        // extractor and the photo store both tolerate a null.
        CaptureResult result = captureService.capture(
                bytes, photo.getContentType(), chain, storeId, observedOn);

        PriceObservation saved = result.observation();
        return observations.views(List.of(saved)).getFirst();
    }
}
