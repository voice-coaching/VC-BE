package org.example.voice.training.infrastructure.storage;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "storage", name = "enabled", havingValue = "true")
public class S3PresignedUrlProvider implements RecordingObjectStoragePort {
    private final ObjectStorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3PresignedUrlProvider(
            ObjectStorageProperties properties,
            S3Client recordingS3Client,
            S3Presigner recordingS3Presigner
    ) {
        this.properties = properties;
        this.s3Client = recordingS3Client;
        this.presigner = recordingS3Presigner;
    }

    @Override
    public String createObjectKey(Long userId, Long sessionId, String fileName) {
        String extension = safeExtension(fileName);
        return "%susers/%d/sessions/%d/%s%s".formatted(
                properties.getRecordingsPrefix(), userId, sessionId, UUID.randomUUID(), extension
        );
    }

    @Override
    public String createUploadUrl(
            String objectKey,
            String mimeType,
            long fileSizeBytes,
            OffsetDateTime expiresAt
    ) {

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(mimeType)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(signatureDuration(expiresAt))
                        .putObjectRequest(objectRequest)
                        .build())
                .url().toString();
    }

    @Override
    public String createPlaybackUrl(String objectKey, OffsetDateTime expiresAt) {

        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(signatureDuration(expiresAt))
                        .getObjectRequest(objectRequest)
                        .build())
                .url().toString();
    }

    @Override
    public Map<String, String> requiredHeaders(String mimeType, long fileSizeBytes) { return Map.of("Content-Type", mimeType); }

    @Override
    public void assertUploadedObject(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            long fileSizeBytes
    ) {
        if (!isUploadedObject(userId, sessionId, objectKey, mimeType, fileSizeBytes))
            throw new BaseException(ErrorCode.UPLOADED_OBJECT_NOT_FOUND);
    }

    @Override
    public boolean isUploadedObject(Long userId, Long sessionId, String objectKey, String mimeType, long fileSizeBytes) {
        String prefix = "%susers/%d/sessions/%d/".formatted(properties.getRecordingsPrefix(), userId, sessionId);
        if (objectKey == null || !objectKey.startsWith(prefix) || objectKey.contains("..") || objectKey.contains("\\"))
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        String relative = objectKey.substring(prefix.length());
        if (relative.isBlank() || relative.contains("/"))
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        try {
            var head = s3Client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(objectKey)
                    .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5)).apiCallAttemptTimeout(Duration.ofSeconds(2)))
                    .build());
            String type = head.contentType() == null ? "" : head.contentType().split(";", 2)[0].trim();
            if (head.contentLength() == null || head.contentLength() != fileSizeBytes || !mimeType.equalsIgnoreCase(type))
                throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
            return true;
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) return false;
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        } catch (SdkException failure) {
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
    }

    @Override
    public void deleteObject(Long userId, Long sessionId, String objectKey) {
        String prefix = "%susers/%d/sessions/%d/".formatted(properties.getRecordingsPrefix(), userId, sessionId);
        // Canonical keys deliberately omit owner IDs. They come from the persisted deletion outbox,
        // whose recording/session association is the authority; retain the normalizer's key format.
        boolean canonical = objectKey != null && objectKey.matches(java.util.regex.Pattern.quote(properties.getRecordingsPrefix())
                + "analysis-(?:audio/[0-9a-f-]{36}\\.wav|video/[0-9a-f-]{36}\\.mp4)");
        if (objectKey == null || (!objectKey.startsWith(prefix) && !canonical) || objectKey.contains("..") || objectKey.contains("\\"))
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
        } catch (SdkException error) {
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
    }

    private static String safeExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        String extension = fileName.substring(fileName.lastIndexOf('.'));
        return extension.matches("\\.[A-Za-z0-9]{1,10}") ? extension : "";
    }

    private static Duration signatureDuration(OffsetDateTime expiresAt) {
        Duration duration = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), expiresAt);

        return duration;
    }
}
