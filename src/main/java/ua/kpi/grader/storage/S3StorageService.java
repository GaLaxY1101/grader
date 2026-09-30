package ua.kpi.grader.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final MinioProperties properties;

    @PostConstruct
    void ensureBucketExists() {
        String bucket = properties.bucket();
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("MinIO bucket already present: {}", bucket);
        } catch (NoSuchBucketException notFound) {
            log.info("Creating MinIO bucket: {}", bucket);
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (AwsServiceException | SdkClientException e) {
            log.error("Failed to verify MinIO bucket {}: {}", bucket, e.getMessage());
            throw e;
        }
    }

    @Override
    public StoredObject put(String key, MultipartFile file) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            s3.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return new StoredObject(key, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new StorageException("Failed to read upload stream for key " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
    }

    @Override
    public void deletePrefix(String keyPrefix) {
        String bucket = properties.bucket();
        String continuationToken = null;
        do {
            ListObjectsV2Request.Builder listBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(keyPrefix);
            if (continuationToken != null) {
                listBuilder.continuationToken(continuationToken);
            }
            ListObjectsV2Response listing = s3.listObjectsV2(listBuilder.build());
            List<ObjectIdentifier> ids = listing.contents().stream()
                    .map(S3Object::key)
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();
            if (!ids.isEmpty()) {
                s3.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(ids).build())
                        .build());
            }
            continuationToken = Boolean.TRUE.equals(listing.isTruncated()) ? listing.nextContinuationToken() : null;
        } while (continuationToken != null);
    }

    @Override
    public void copy(String sourceKey, String destinationKey) {
        s3.copyObject(CopyObjectRequest.builder()
                .sourceBucket(properties.bucket())
                .sourceKey(sourceKey)
                .destinationBucket(properties.bucket())
                .destinationKey(destinationKey)
                .build());
    }

    @Override
    public URI presignGet(String key, Duration ttl) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getRequest)
                .build();
        return URI.create(presigner.presignGetObject(presignRequest).url().toString());
    }
}
