package org.example.voice.auth.domain.model;

import org.example.voice.user.domain.type.UserRole;
import java.time.OffsetDateTime;

public record TokenClaims(Long userId, UserRole role, String sessionId, OffsetDateTime expiresAt) {
}
