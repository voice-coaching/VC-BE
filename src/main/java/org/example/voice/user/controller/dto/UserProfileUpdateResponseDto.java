package org.example.voice.user.controller.dto;

import org.example.voice.user.domain.model.UpdatedUserProfile;

import java.time.OffsetDateTime;

public record UserProfileUpdateResponseDto(Long id, String nickname, OffsetDateTime updatedAt) {
    public static UserProfileUpdateResponseDto from(UpdatedUserProfile profile) {
        return new UserProfileUpdateResponseDto(profile.id(), profile.nickname(), profile.updatedAt());
    }
}
