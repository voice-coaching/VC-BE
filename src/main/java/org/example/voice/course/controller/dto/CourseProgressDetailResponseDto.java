package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseProgressData;

import java.time.OffsetDateTime;

public record CourseProgressDetailResponseDto(
        Long courseId,
        String status,
        Long lastStepId,
        Double progressPercent,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {

    public static CourseProgressDetailResponseDto from(CourseProgressData data) {
        return new CourseProgressDetailResponseDto(
                data.courseId(),
                data.status().name(),
                data.lastStepId(),
                data.progressPercent(),
                data.startedAt(),
                data.completedAt()
        );
    }
}
