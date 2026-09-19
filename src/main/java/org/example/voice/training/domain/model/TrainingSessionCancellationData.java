package org.example.voice.training.domain.model;

import org.example.voice.training.domain.type.TrainingSessionStatus;

import java.time.OffsetDateTime;

public record TrainingSessionCancellationData(
        Long sessionId,
        TrainingSessionStatus status,
        OffsetDateTime canceledAt
) {
}
