package org.example.voice.training.domain.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.example.voice.training.domain.model.RecordingUploadIntentData;

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
    Optional<Long> reserveForRegistration(
            Long userId,
            Long sessionId,
            String objectKey,
            String mimeType,
            Long fileSizeBytes,
            String registrationSha256
    );

    void markConsumed(Long userId, Long sessionId, String objectKey, Long recordingId, String registrationSha256);

    Optional<RecordingUploadIntentData> find(Long userId, Long sessionId, String objectKey);

    List<RecordingUploadIntentData> recent(Long userId, Long sessionId, int limit);

    void expireForSession(Long userId, Long sessionId);

    void expireForUser(Long userId);
}
