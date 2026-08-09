package org.example.voice.training.controller.dto;

import org.example.voice.training.domain.model.TrainingSessionCreatedData;

import java.time.OffsetDateTime;

public record TrainingSessionResponseDto(
        Long sessionId,
        Long contentId,
        Long courseStepId,
        String learningFocus,
        String status,
        OffsetDateTime startedAt
) {

    public static TrainingSessionResponseDto from(TrainingSessionCreatedData data) {
        return new TrainingSessionResponseDto(
                data.sessionId(),
                data.contentId(),
                data.courseStepId(),
                data.learningFocus().name(),
                data.status().name(),
                data.startedAt()
        );
    }
}
