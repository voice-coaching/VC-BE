package org.example.voice.title.controller.dto;

import org.example.voice.title.application.TitleService;

import java.time.OffsetDateTime;

public record TitleProgressResponseDto(
        String code,
        String label,
        long completedTrainingCount,
        long minimumTrainingCount,
        TitleNextResponseDto next,
        OffsetDateTime updatedAt
) {
    public static TitleProgressResponseDto from(TitleService.Progress progress) {
        return new TitleProgressResponseDto(
                progress.code(),
                progress.label(),
                progress.completedTrainingCount(),
                progress.minimumTrainingCount(),
                TitleNextResponseDto.from(progress.next()),
                progress.updatedAt()
        );
    }
}
