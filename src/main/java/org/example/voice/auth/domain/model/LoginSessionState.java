package org.example.voice.auth.domain.model;

import java.time.OffsetDateTime;

/** No access/refresh credential is part of a session lookup projection. */
public record LoginSessionState(String sessionId, OffsetDateTime expiresAt) {
    public boolean accepts(String candidate, OffsetDateTime now) {
        return sessionId != null && sessionId.equals(candidate) && expiresAt != null && expiresAt.isAfter(now);
    }

    @Override public String toString() { return "LoginSessionState[redacted]"; }
}
