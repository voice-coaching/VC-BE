package org.example.voice.training.domain.port;

import java.time.OffsetDateTime;
import java.util.Map;

public interface RecordingObjectStoragePort {
    String createObjectKey(Long userId, Long sessionId, String fileName);

    String createUploadUrl(
            String objectKey,
            String mimeType,
            long fileSizeBytes,
            OffsetDateTime expiresAt
    );

    String createPlaybackUrl(String objectKey, OffsetDateTime expiresAt);

    Map<String, String> requiredHeaders(String mimeType, long fileSizeBytes);

    void assertUploadedObject(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            long fileSizeBytes
    );

    void deleteObject(Long userId, Long sessionId, String objectKey);
}
