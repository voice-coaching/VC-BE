package org.example.voice.notification.infrastructure;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeFcmRetryTest {
    @Test void parsesRetryAfterWithoutRetryingEarlierThanRequested() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertEquals(120, NativeFcmDispatcher.parseRetryAfter("120", now));
        assertEquals(180, NativeFcmDispatcher.parseRetryAfter(List.of("Thu, 1 Jan 2026 00:03:00 GMT"), now));
        assertEquals(180, NativeFcmDispatcher.parseRetryAfter("Thu, 1 Jan 2026 00:03:00 GMT", now.plusMillis(500)));
        assertEquals(86400, NativeFcmDispatcher.parseRetryAfter("999999", now));
        assertEquals(0, NativeFcmDispatcher.parseRetryAfter("invalid", now));
    }
}
