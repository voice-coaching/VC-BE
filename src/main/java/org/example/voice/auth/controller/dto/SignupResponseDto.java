package org.example.voice.auth.controller.dto;

import org.example.voice.auth.domain.model.AuthSession;

public record SignupResponseDto(Long userId, String email, String nickname, String accessToken,
                                String tokenType, long expiresIn, boolean onboardingRequired) {
    public static SignupResponseDto from(AuthSession session) {
        return new SignupResponseDto(session.user().getId(), session.user().getEmail(), session.user().getNickname(),
                session.accessToken(), "Bearer", session.expiresIn(), !session.onboardingCompleted());
    }
}
