package org.example.voice.user.domain.model;

import java.time.OffsetDateTime;

public record UpdatedUserProfile(Long id, String nickname, OffsetDateTime updatedAt) {
}
