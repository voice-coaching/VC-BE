package org.example.voice.title.controller.dto;

import org.example.voice.title.application.TitleService;

public record TitleNextResponseDto(
        String code,
        String label,
        long requiredTrainingCount,
        long remainingTrainingCount,
        int passingScore,
        boolean eligible
) {
    public static TitleNextResponseDto from(TitleService.Next next) {
        return next == null ? null : new TitleNextResponseDto(
                next.code(),
                next.label(),
                next.requiredTrainingCount(),
                next.remainingTrainingCount(),
                next.passingScore(),
                next.eligible()
        );
    }
}
