package app.pricelog.api.web;

import app.pricelog.api.capture.CaptureResult;
import app.pricelog.api.capture.CaptureService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/captures")
public class CaptureController {

    private final CaptureService captureService;

    public CaptureController(CaptureService captureService) {
        this.captureService = captureService;
    }

    /**
     * The whole app in one call: upload a tag photo, get back a saved,
     * fully-interpreted observation ready for confirmation.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ObservationView capture(
            @RequestPart("photo") MultipartFile photo,
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

        CaptureResult result = captureService.capture(
                bytes, photo.getContentType(), storeId, observedOn);

        return ObservationView.of(result.observation());
    }
}
