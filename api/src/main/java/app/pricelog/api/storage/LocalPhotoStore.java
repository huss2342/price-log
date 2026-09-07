package app.pricelog.api.storage;

import app.pricelog.api.config.PriceLogProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development store: writes photos under a local directory. */
@Component
@ConditionalOnProperty(name = "pricelog.photos.mode", havingValue = "local", matchIfMissing = true)
public class LocalPhotoStore implements PhotoStore {

    private final Path root;

    public LocalPhotoStore(PriceLogProperties properties) {
        this.root = Path.of(properties.getPhotos().getLocalDir()).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] bytes, String contentType, String filenameHint) {
        String key = LocalDate.now() + "/" + UUID.randomUUID() + extensionFor(contentType);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write photo to " + target, e);
        }
        return key;
    }

    @Override
    public Optional<StoredPhoto> load(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            String contentType = Optional.ofNullable(Files.probeContentType(target))
                    .orElse("application/octet-stream");
            return Optional.of(new StoredPhoto(Files.readAllBytes(target), contentType));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read photo " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete photo " + key, e);
        }
    }

    /** Resolves inside the root and rejects keys that try to climb out of it. */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Photo key escapes the storage root: " + key);
        }
        return target;
    }

    private String extensionFor(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic", "image/heif" -> ".heic";
            default -> ".jpg";
        };
    }
}
