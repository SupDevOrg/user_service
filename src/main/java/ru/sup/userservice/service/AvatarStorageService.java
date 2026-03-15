package ru.sup.userservice.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.sup.userservice.dto.response.AvatarUploadUrlResponse;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AvatarStorageService {

    private static final long MIN_EXPIRY_SECONDS = 60;
    private static final long MAX_EXPIRY_SECONDS = 7L * 24 * 60 * 60;

    private final MinioClient minioClient;
    private final String bucket;
    private final String publicBaseUrl;
    private final int uploadUrlExpirySeconds;

    public AvatarStorageService(
            @Value("${storage.s3.endpoint:${AWS_ENDPOINT_URL:http://localhost:9000}}") String endpoint,
            @Value("${storage.s3.access-key:${AWS_ACCESS_KEY_ID:minioadmin}}") String accessKey,
            @Value("${storage.s3.secret-key:${AWS_SECRET_ACCESS_KEY:minioadmin}}") String secretKey,
            @Value("${storage.s3.bucket:${AWS_S3_BUCKET_NAME:avatars}}") String bucket,
            @Value("${storage.s3.region:${AWS_DEFAULT_REGION:us-east-1}}") String region,
            @Value("${storage.s3.public-base-url:${AWS_ENDPOINT_URL:http://localhost:9000}}") String publicBaseUrl,
            @Value("${storage.s3.upload-url-expiry-seconds:900}") long uploadUrlExpirySeconds
    ) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.uploadUrlExpirySeconds = normalizeExpiry(uploadUrlExpirySeconds);
        log.info("Avatar storage initialized: bucket={}, region={}", bucket, region);
    }

    public AvatarUploadUrlResponse createAvatarUploadUrl(Long userId, String contentType, String fileName) {
        String normalizedContentType = normalizeContentType(contentType);
        String objectKey = buildObjectKey(userId, normalizedContentType, fileName);

        try {
            ensureBucketExists();
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(uploadUrlExpirySeconds)
                            .build()
            );

            String avatarUrl = publicBaseUrl + "/" + bucket + "/" + objectKey;
            return new AvatarUploadUrlResponse(uploadUrl, avatarUrl, objectKey, uploadUrlExpirySeconds);
        } catch (Exception e) {
            log.error("Failed to create avatar upload URL for user {}", userId, e);
            throw new IllegalStateException("Cannot create upload URL", e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucket)
                        .build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucket)
                            .build()
            );
            log.info("Bucket '{}' was created", bucket);
        }
    }

    private String buildObjectKey(Long userId, String contentType, String fileName) {
        String extension = resolveExtension(contentType, fileName);
        return "avatars/" + userId + "/" + UUID.randomUUID() + extension;
    }

    private String resolveExtension(String contentType, String fileName) {
        Map<String, String> byMime = Map.of(
                "image/jpeg", ".jpg",
                "image/png", ".png",
                "image/webp", ".webp"
        );

        if (byMime.containsKey(contentType)) {
            return byMime.get(contentType);
        }

        if (fileName != null) {
            String normalized = fileName.toLowerCase(Locale.ROOT);
            if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
                return ".jpg";
            }
            if (normalized.endsWith(".png")) {
                return ".png";
            }
            if (normalized.endsWith(".webp")) {
                return ".webp";
            }
        }

        return ".jpg";
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }

        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        if (!normalized.equals("image/jpeg")
                && !normalized.equals("image/png")
                && !normalized.equals("image/webp")) {
            throw new IllegalArgumentException("Only image/jpeg, image/png, image/webp are allowed");
        }
        return normalized;
    }

    private int normalizeExpiry(long value) {
        long bounded = Math.max(MIN_EXPIRY_SECONDS, Math.min(MAX_EXPIRY_SECONDS, value));
        return Math.toIntExact(bounded);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:9000";
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
