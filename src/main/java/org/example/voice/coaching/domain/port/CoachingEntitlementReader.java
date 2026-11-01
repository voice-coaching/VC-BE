package org.example.voice.coaching.domain.port;

import org.example.voice.coaching.domain.CoachingWindow;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface CoachingEntitlementReader {
    /** Empty means the account's one-time trial, not a new trial grant. */
    Optional<CoachingWindow> find(Long userId, OffsetDateTime now);
}
