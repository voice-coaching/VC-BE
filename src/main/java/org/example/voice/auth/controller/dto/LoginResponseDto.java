package org.example.voice.auth.controller.dto;
import org.example.voice.auth.domain.model.AuthSession;
public record LoginResponseDto(String accessToken, String tokenType, long expiresIn, LoginUserDto user) {
    public static LoginResponseDto from(AuthSession session) {
        return new LoginResponseDto(session.accessToken(), "Bearer", session.expiresIn(),
                new LoginUserDto(session.user().getId(), session.user().getNickname(), session.onboardingCompleted()));
    }
}
