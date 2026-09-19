package org.example.voice.auth.domain.model;

import java.time.OffsetDateTime;

public record IssuedTokens(String accessToken, String refreshToken, String sessionId, OffsetDateTime refreshExpiresAt) {
}
