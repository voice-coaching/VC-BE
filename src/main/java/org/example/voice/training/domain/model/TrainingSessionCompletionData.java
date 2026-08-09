package org.example.voice.training.domain.model;

import org.example.voice.training.domain.type.TrainingSessionStatus;

import java.time.OffsetDateTime;

public record TrainingSessionCompletionData(
        Long sessionId,
        TrainingSessionStatus status,
        OffsetDateTime completedAt
) {
}
