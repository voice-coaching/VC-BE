package org.example.voice.title.controller.dto;

import org.example.voice.title.application.TitleService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TitleExamGradeResponseDto(
        Long examId,
        String status,
        BigDecimal score,
        int passingScore,
        boolean passed,
        String previousTitle,
        String currentTitle,
        OffsetDateTime evaluatedAt
) {
    public static TitleExamGradeResponseDto from(TitleService.Grade grade) {
        return new TitleExamGradeResponseDto(
                grade.examId(),
                grade.status(),
                grade.score(),
                grade.passingScore(),
                grade.passed(),
                grade.previousTitle(),
                grade.currentTitle(),
                grade.evaluatedAt()
        );
    }
}
