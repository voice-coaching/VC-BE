package org.example.voice.auth.domain.model;

import org.example.voice.user.domain.entity.User;

public record AuthSession(User user, String accessToken, String refreshToken, long expiresIn, boolean newUser, boolean onboardingCompleted) {
}
