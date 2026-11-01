package org.example.voice.training.controller.dto;

import org.example.voice.practicecontent.domain.type.LearningFocus;

public record TrainingSessionCreateRequestDto(
        Long contentId, Long courseStepId, LearningFocus learningFocus, Long titleExamId,
        Integer sentenceIndex, String sentenceText
) {
    public TrainingSessionCreateRequestDto(Long contentId, Long courseStepId, LearningFocus learningFocus) {
        this(contentId,courseStepId,learningFocus,null,null,null);
    }
    public TrainingSessionCreateRequestDto(Long contentId, Long courseStepId, LearningFocus learningFocus, Long titleExamId) {
        this(contentId,courseStepId,learningFocus,titleExamId,null,null);
    }
}
