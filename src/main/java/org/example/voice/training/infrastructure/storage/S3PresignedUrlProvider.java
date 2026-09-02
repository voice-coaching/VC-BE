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
        requireOwnedKey(objectKey);
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(mimeType)
                .contentLength(fileSizeBytes)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(signatureDuration(expiresAt))
                        .putObjectRequest(objectRequest)
                        .build())
                .url().toString();
    }

    @Override
    public String createPlaybackUrl(String objectKey, OffsetDateTime expiresAt) {
        requireOwnedKey(objectKey);
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
    public Map<String, String> requiredHeaders(String mimeType, long fileSizeBytes) {
        return Map.of(
                "Content-Type", mimeType,
                "Content-Length", Long.toString(fileSizeBytes)
        );
    }

    @Override
    public void assertUploadedObject(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            long fileSizeBytes
    ) {
        requireOwnedKey(userId, sessionId, objectKey);
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
            if (response.contentLength() == null
                    || response.contentLength() != fileSizeBytes
                    || response.contentType() == null
                    || !response.contentType().equalsIgnoreCase(mimeType)) {
                throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
            }
        } catch (S3Exception error) {
            if (error.statusCode() == 404) {
                throw new BaseException(ErrorCode.UPLOADED_OBJECT_NOT_FOUND);
            }
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        } catch (SdkException error) {
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
    }

    private void requireOwnedKey(String objectKey) {
        if (objectKey == null
                || !objectKey.startsWith(properties.getRecordingsPrefix())
                || objectKey.length() <= properties.getRecordingsPrefix().length()
                || objectKey.startsWith("/")
                || objectKey.contains("\\")
                || java.util.Arrays.stream(objectKey.split("/"))
                        .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."))) {
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
        }
    }

    private void requireOwnedKey(Long userId, Long sessionId, String objectKey) {
        if (userId == null || userId <= 0 || sessionId == null || sessionId <= 0) {
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
        }
        requireOwnedKey(objectKey);
        String ownerPrefix = "%susers/%d/sessions/%d/".formatted(
                properties.getRecordingsPrefix(), userId, sessionId
        );
        if (!objectKey.startsWith(ownerPrefix) || objectKey.length() <= ownerPrefix.length()) {
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        }
    }

    private static String safeExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        String extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
        return extension.matches("\\.(webm|mp3|wav)") ? extension : "";
    }

    private static Duration signatureDuration(OffsetDateTime expiresAt) {
        Duration duration = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), expiresAt);
        if (duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
        }
        return duration;
    }
}
