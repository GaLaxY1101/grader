package ua.kpi.grader.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint       URL the backend uses to reach the object store
 * @param publicEndpoint URL browsers use for presigned download links; defaults to {@code endpoint}.
 *                       Differs when the backend runs in a container network (e.g. {@code http://s3:9090}
 *                       internally vs {@code http://server:9000} for browsers).
 */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket,
        int presignTtlSeconds
) {

    /**
     * Returns the endpoint to embed in presigned URLs handed to browsers.
     */
    public String effectivePublicEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
}
