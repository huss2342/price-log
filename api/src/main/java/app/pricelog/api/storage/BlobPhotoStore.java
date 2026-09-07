package app.pricelog.api.storage;

import app.pricelog.api.config.PriceLogProperties;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production store: Azure Blob Storage, cool tier. The container stays private;
 * bytes are served back through the API rather than by a public blob URL.
 */
@Component
@ConditionalOnProperty(name = "pricelog.photos.mode", havingValue = "blob")
public class BlobPhotoStore implements PhotoStore {

    private final BlobContainerClient container;

    public BlobPhotoStore(PriceLogProperties properties) {
        PriceLogProperties.Photos config = properties.getPhotos();
        this.container = new BlobServiceClientBuilder()
                .connectionString(config.getConnectionString())
                .buildClient()
                .getBlobContainerClient(config.getContainer());
        if (!container.exists()) {
            container.create();
        }
    }

    @Override
    public String store(byte[] bytes, String contentType, String filenameHint) {
        String key = LocalDate.now() + "/" + UUID.randomUUID() + extensionFor(contentType);
        var blob = container.getBlobClient(key);
        blob.upload(new ByteArrayInputStream(bytes), bytes.length, true);
        blob.setHttpHeaders(new BlobHttpHeaders()
                .setContentType(contentType == null ? "image/jpeg" : contentType));
        return key;
    }

    @Override
    public Optional<StoredPhoto> load(String key) {
        var blob = container.getBlobClient(key);
        try {
            var buffer = new ByteArrayOutputStream();
            blob.downloadStream(buffer);
            String contentType = Optional.ofNullable(blob.getProperties().getContentType())
                    .orElse("application/octet-stream");
            return Optional.of(new StoredPhoto(buffer.toByteArray(), contentType));
        } catch (BlobStorageException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        container.getBlobClient(key).deleteIfExists();
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
