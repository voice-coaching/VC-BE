package org.example.voice.coaching.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** One non-renewing trial or one paid entitlement; a TEST grant is not a subscription. */
public record CoachingWindow(String key, String membershipType, String environment,
                             OffsetDateTime startsAt, OffsetDateTime expiresAt, Integer limit) {
    public static CoachingWindow trial(OffsetDateTime start) {
        var utc = start.withOffsetSameInstant(ZoneOffset.UTC);
        return new CoachingWindow("free", "free", null, utc, utc.plusMonths(1), 8);
    }
    public static CoachingWindow admin() {
        return new CoachingWindow("admin", "admin", "TEST", null, null, null);
    }
    public boolean active(OffsetDateTime now) {
        return limit == null || (!now.isBefore(startsAt) && now.isBefore(expiresAt));
    }
}
