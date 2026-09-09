package org.example.voice.training.infrastructure;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "storage", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PresignedUrlProvider implements RecordingObjectStoragePort {

    @Override
    public String createObjectKey(Long userId, Long sessionId, String fileName) {
        String extension = "";
        if (fileName != null && fileName.contains(".")) {
            String candidate = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
            extension = candidate.matches("\\.(webm|mp3|wav|mp4|mov)") ? candidate : "";
        }
        return "recordings/users/%d/sessions/%d/%s%s".formatted(userId, sessionId, UUID.randomUUID(), extension);
    }

    @Override
    public String createUploadUrl(
            String objectKey,
            String mimeType,
            long fileSizeBytes,
            OffsetDateTime expiresAt
    ) {
        throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }

    @Override
    public String createPlaybackUrl(String objectKey, OffsetDateTime expiresAt) {
        throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
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
        String ownerPrefix = "recordings/users/%d/sessions/%d/".formatted(userId, sessionId);
        if (objectKey == null
                || !objectKey.startsWith(ownerPrefix)
                || objectKey.length() <= ownerPrefix.length()
                || objectKey.substring(ownerPrefix.length()).contains("/")) {
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        }
        throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }

    @Override
    public void deleteObject(Long userId, Long sessionId, String objectKey) {
        String ownerPrefix = "recordings/users/%d/sessions/%d/".formatted(userId, sessionId);
        if (objectKey == null || !objectKey.startsWith(ownerPrefix)) {
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        }
        String relativeKey = objectKey.substring(ownerPrefix.length());
        if (relativeKey.isBlank()
                || (relativeKey.contains("/")
                && !relativeKey.matches("normalized/[0-9a-fA-F-]{36}\\.wav"))) {
            throw new BaseException(ErrorCode.RECORDING_ACCESS_DENIED);
        }
        // A disabled provider must leave the durable deletion request retryable.
        throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }
}
