package org.example.voice.coaching.domain;

import java.time.OffsetDateTime;

public record CoachingUsage(String membershipType, String environment, boolean unlimited,
                            OffsetDateTime startsAt, OffsetDateTime expiresAt, Integer limit,
                            long used, long reserved, Long remaining, boolean canRequestCoaching) {
    public static CoachingUsage of(CoachingWindow window, long used, long reserved, OffsetDateTime now) {
        boolean unlimited = window.limit() == null;
        Long remaining = unlimited ? null : window.active(now)
                ? Math.max(0L, window.limit() - used - reserved) : 0L;
        return new CoachingUsage(window.membershipType(), window.environment(), unlimited,
                window.startsAt(), window.expiresAt(), window.limit(), used, reserved, remaining,
                window.active(now) && (unlimited || remaining > 0));
    }
}
