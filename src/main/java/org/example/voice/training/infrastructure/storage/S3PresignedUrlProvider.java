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
    public Map<String, String> requiredHeaders(String mimeType, long fileSizeBytes) { return Map.of(); }

    @Override
    public void assertUploadedObject(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            long fileSizeBytes
    ) {}

    @Override
    public void deleteObject(Long userId, Long sessionId, String objectKey) {

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
        return fileName == null || !fileName.contains(".") ? "" : fileName.substring(fileName.lastIndexOf('.'));
    }

    private static Duration signatureDuration(OffsetDateTime expiresAt) {
        Duration duration = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), expiresAt);

        return duration;
    }
}
