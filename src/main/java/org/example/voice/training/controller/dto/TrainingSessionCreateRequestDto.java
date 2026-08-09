package org.example.voice.training.controller.dto;

import org.example.voice.practicecontent.domain.type.LearningFocus;

public record TrainingSessionCreateRequestDto(
        Long contentId,
        Long courseStepId,
        LearningFocus learningFocus
) {
}
