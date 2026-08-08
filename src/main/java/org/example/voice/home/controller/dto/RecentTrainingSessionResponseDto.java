package org.example.voice.home.controller.dto;

import org.example.voice.home.domain.model.RecentTrainingData;

import java.time.OffsetDateTime;

public record RecentTrainingSessionResponseDto(
        Long sessionId,
        Long contentId,
        String contentTitle,
        String status,
        String resumeType,
        OffsetDateTime lastUpdatedAt
) {

    public static RecentTrainingSessionResponseDto from(RecentTrainingData data) {
        return new RecentTrainingSessionResponseDto(
                data.sessionId(),
                data.contentId(),
                data.contentTitle(),
                data.status().name(),
                data.resumeType(),
                data.lastUpdatedAt()
        );
    }
}
