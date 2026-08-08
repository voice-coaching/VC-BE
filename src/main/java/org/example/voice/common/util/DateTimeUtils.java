package org.example.voice.common.util;

import java.time.Clock;
import java.time.OffsetDateTime;

public final class DateTimeUtils {

    private DateTimeUtils() {
    }

    public static OffsetDateTime now(Clock clock) {
        return OffsetDateTime.now(clock);
    }
}
