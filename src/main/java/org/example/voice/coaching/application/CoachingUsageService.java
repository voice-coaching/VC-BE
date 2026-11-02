package org.example.voice.coaching.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.coaching.domain.*;
import org.example.voice.coaching.domain.port.*;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CoachingUsageService implements CoachingUsageReservation {
    private final CoachingUsageStore store;
    private final CoachingEntitlementReader entitlements;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CoachingUsage getUsage(Long userId) {
        var now = OffsetDateTime.now(clock);
        var window = window(userId, now);
        var counts = store.counts(userId, window.key());
        return CoachingUsage.of(window, counts.used(), counts.reserved(), now);
    }

    /** Joins analysis creation/retry and outbox insertion, so rejection rolls back the entire request. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void reserve(Long userId, Long analysisId, Long recordingId, UUID requestId,
                        String script, Integer durationMs) {
        // Resource limits apply to all plans, including the TEST admin quota exemption.

        store.lockAccount(userId);
        var now = OffsetDateTime.now(clock);
        var window = window(userId, now);
        if (window.limit() == null) return;

        var counts = store.counts(userId, window.key());

        store.reserve(userId, analysisId, recordingId, requestId, window);
    }

    private CoachingWindow window(Long userId, OffsetDateTime now) {
        var start = store.trialStartedAt(userId);
        return entitlements.find(userId, now).orElseGet(() -> CoachingWindow.trial(start));
    }
}
