package ua.kpi.grader.storage;

import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Duration;

public interface StorageService {

    /**
     * Uploads the given multipart file under the provided storage key.
     *
     * @param key  fully-qualified object key (e.g. {@code assignments/1/uuid_report.pdf})
     * @param file source multipart file
     * @return metadata describing the stored object
     */
    StoredObject put(String key, MultipartFile file);

    /**
     * Deletes a single object by key. Silent if the object does not exist.
     */
    void delete(String key);

    /**
     * Deletes every object whose key starts with the given prefix.
     * Used to clean up all attachments of a deleted parent (assignment/submission).
     */
    void deletePrefix(String keyPrefix);

    /**
     * Generates a short-lived pre-signed URL granting read access to the object.
     *
     * @param key object key
     * @param ttl how long the URL remains valid
     * @return absolute presigned URL
     */
    URI presignGet(String key, Duration ttl);

    /**
     * Server-side copy of an existing object to a new key inside the same bucket.
     * Used by the template instantiate flow to snapshot attachments into a course.
     */
    void copy(String sourceKey, String destinationKey);
}
