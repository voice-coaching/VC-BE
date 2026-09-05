package org.example.voice.training.domain.port;

import org.example.voice.training.domain.type.RecordingDeletionReason;

public interface RecordingDeletionScheduler {

    void schedule(Long userId, Long sessionId, String objectKey, RecordingDeletionReason reason);

    void scheduleAllForUser(Long userId, RecordingDeletionReason reason);
}
