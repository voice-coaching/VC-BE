package org.example.voice.coaching.domain.port;

import org.example.voice.coaching.domain.CoachingWindow;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface CoachingUsageStore {
    void lockAccount(Long userId);
    /** Returns the later of signup and policy rollout; verifies the account is active. */
    OffsetDateTime trialStartedAt(Long userId);
    Counts counts(Long userId, String windowKey);
    void reserve(Long userId, Long analysisId, Long recordingId, UUID requestId, CoachingWindow window);
    record Counts(long used, long reserved) {}
}
