package org.example.voice.auth.domain.entity;

import java.time.OffsetDateTime;

public record RefreshToken(
        Long userId,
        String token,
        OffsetDateTime expiresAt
) {
}
