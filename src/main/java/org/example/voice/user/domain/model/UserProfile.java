package org.example.voice.user.domain.model;

import org.example.voice.user.domain.type.UserStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record UserProfile(
        Long id,
        String email,
        String nickname,
        UserStatus status,
        List<String> loginProviders,
        boolean onboardingCompleted,
        OffsetDateTime createdAt
) {
}
