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

    /**
     * Locks the mutable session and issued upload until the registration
     * transaction completes. Must run inside the caller's transaction.
     */
    void reserveForRegistration(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            Long fileSizeBytes
    );

    void markConsumed(Long userId, Long sessionId, String objectKey);

    void expireForSession(Long userId, Long sessionId);

    void expireForUser(Long userId);
}
