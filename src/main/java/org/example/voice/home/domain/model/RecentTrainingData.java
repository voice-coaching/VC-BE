package org.example.voice.home.domain.model;

import org.example.voice.training.domain.type.TrainingSessionStatus;

import java.time.OffsetDateTime;

public record RecentTrainingData(
        Long sessionId,
        Long contentId,
        String contentTitle,
        TrainingSessionStatus status,
        String resumeType,
        OffsetDateTime lastUpdatedAt
) {
}
