package org.example.voice.coaching.domain.port;

import java.util.UUID;

public interface CoachingUsageReservation {
    void reserve(Long userId, Long analysisId, Long recordingId, UUID requestId, String script, Integer durationMs);
}
