package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseProgressData;

import java.time.OffsetDateTime;

public record CourseCompleteResponseDto(
        Long courseId,
        String status,
        Double progressPercent,
        OffsetDateTime completedAt
) {

    public static CourseCompleteResponseDto from(CourseProgressData data) {
        return new CourseCompleteResponseDto(
                data.courseId(),
                data.status().name(),
                data.progressPercent(),
                data.completedAt()
        );
    }
}
