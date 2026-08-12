package org.example.voice.auth.controller.dto;
import org.example.voice.auth.domain.model.AuthSession;
public record SocialLoginResponseDto(String accessToken, String tokenType, long expiresIn, boolean isNewUser,
                                     boolean onboardingRequired, SocialUserDto user) {
    public static SocialLoginResponseDto from(AuthSession session) {
        return new SocialLoginResponseDto(session.accessToken(), "Bearer", session.expiresIn(), session.newUser(),
                !session.onboardingCompleted(), new SocialUserDto(session.user().getId(), session.user().getEmail(), session.user().getNickname()));
    }
}
