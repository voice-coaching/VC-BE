package org.example.voice.training.domain.port;

import java.time.OffsetDateTime;

public interface RecordingUploadIntentRegistry {

    void recordIssued(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            Long fileSizeBytes,
            OffsetDateTime expiresAt
    );

    void markConsumed(Long userId, Long sessionId, String objectKey);

    void expireForSession(Long userId, Long sessionId);

    void expireForUser(Long userId);
}
