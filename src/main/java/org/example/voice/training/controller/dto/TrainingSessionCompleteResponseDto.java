package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.TrainingSessionCompletionData;

import java.time.OffsetDateTime;

public record TrainingSessionCompleteResponseDto(
        Long sessionId,
        String status,
        OffsetDateTime completedAt
) {

    public static TrainingSessionCompleteResponseDto from(TrainingSessionCompletionData data) {
        return new TrainingSessionCompleteResponseDto(data.sessionId(), data.status().name(), data.completedAt());
    }
}
