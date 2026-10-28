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
        OffsetDateTime createdAt,
        String profileImageUrl,
        org.example.voice.payment.domain.Membership membership
) {
    public UserProfile(Long id, String email, String nickname, UserStatus status, List<String> providers,
                       boolean onboardingCompleted, OffsetDateTime createdAt, String profileImageUrl) {
        this(id, email, nickname, status, providers, onboardingCompleted, createdAt, profileImageUrl,
                org.example.voice.payment.domain.Membership.free());
    }
}
