package org.example.voice.auth.domain.entity;

public record AuthToken(
        String accessToken,
        String refreshToken
) {
}
