package org.example.voice.title.controller.dto;

import org.example.voice.title.application.TitleService;

import java.time.OffsetDateTime;

public record TitleExamResponseDto(
        Long id,
        String currentTitle,
        String targetTitle,
        Long practiceContentId,
        long requiredTrainingCount,
        int passingScore,
        String status,
        OffsetDateTime createdAt,
        Long trainingSessionId
) {
    public static TitleExamResponseDto from(TitleService.Exam exam) {
        return new TitleExamResponseDto(
                exam.id(),
                exam.currentTitle(),
                exam.targetTitle(),
                exam.practiceContentId(),
                exam.requiredTrainingCount(),
                exam.passingScore(),
                exam.status(),
                exam.createdAt(),
                exam.trainingSessionId()
        );
    }
}
