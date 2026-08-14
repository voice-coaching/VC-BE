package org.example.voice.user.controller.dto;

import org.example.voice.user.domain.model.UserProfile;
import java.time.OffsetDateTime;
import java.util.List;

public record UserProfileResponseDto(
        Long id,
        String email,
        String nickname,
        String status,
        List<String> loginProviders,
        boolean onboardingCompleted,
        OffsetDateTime createdAt
) {
    public static UserProfileResponseDto from(UserProfile profile) {
        return new UserProfileResponseDto(
                profile.id(), profile.email(), profile.nickname(), profile.status().name(),
                profile.loginProviders(), profile.onboardingCompleted(), profile.createdAt());
    }
}
