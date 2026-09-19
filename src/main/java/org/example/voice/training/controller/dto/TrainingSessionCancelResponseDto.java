package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.TrainingSessionCancellationData;

import java.time.OffsetDateTime;

public record TrainingSessionCancelResponseDto(
        Long sessionId,
        String status,
        OffsetDateTime canceledAt
) {

    public static TrainingSessionCancelResponseDto from(TrainingSessionCancellationData data) {
        return new TrainingSessionCancelResponseDto(data.sessionId(), data.status().name(), data.canceledAt());
    }
}
