package app.pricelog.api.web;

import app.pricelog.api.storage.PhotoStore;
import java.util.NoSuchElementException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;

/**
 * Serves tag photos back from private storage. Keeping this behind the API is
 * what lets the storage container stay private with no public blob access.
 */
@RestController
@RequestMapping("/api/photos")
@Tag(name = "photos", description = "Tag photos from private storage")
public class PhotoController {

    private final PhotoStore photos;

    public PhotoController(PhotoStore photos) {
        this.photos = photos;
    }

    @Operation(summary = "One tag photo's bytes")
    @GetMapping("/**")
    public ResponseEntity<byte[]> get(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String key = path.substring("/api/photos/".length());

        PhotoStore.StoredPhoto photo = photos.load(key)
                .orElseThrow(() -> new NoSuchElementException("No photo " + key));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                // Photos are immutable once written, so cache them hard.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate())
                .body(photo.bytes());
    }
}
