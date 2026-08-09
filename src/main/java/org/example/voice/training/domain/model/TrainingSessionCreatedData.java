package org.example.voice.training.domain.model;

import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.type.TrainingSessionStatus;

import java.time.OffsetDateTime;

public record TrainingSessionCreatedData(
        Long sessionId,
        Long contentId,
        Long courseStepId,
        LearningFocus learningFocus,
        TrainingSessionStatus status,
        OffsetDateTime startedAt
) {
}
