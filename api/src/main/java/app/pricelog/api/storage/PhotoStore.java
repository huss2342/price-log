package app.pricelog.api.storage;

import java.util.Optional;

/**
 * Where captured tag photos live. Photos are addressed by an opaque key rather
 * than a public URL so the storage container can stay private; the API serves
 * the bytes back through an authenticated endpoint.
 */
public interface PhotoStore {

    /** @return the key to persist on the observation */
    String store(byte[] bytes, String contentType, String filenameHint);

    Optional<StoredPhoto> load(String key);

    /**
     * Removes a stored photo. Deleting an absent key is not an error, so this
     * is safe to call during cleanup without first checking existence.
     */
    void delete(String key);

    record StoredPhoto(byte[] bytes, String contentType) {
    }
}
