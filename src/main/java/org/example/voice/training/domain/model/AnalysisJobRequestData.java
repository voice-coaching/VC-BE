package org.example.voice.training.domain.model;

import org.example.voice.practicecontent.domain.type.LearningFocus;

public record AnalysisJobRequestData(
        Long analysisId,
        Long sessionId,
        Long recordingId,
        Long userId,
        String audioUrl,
        String scriptText,
        LearningFocus learningFocus
) {
}
