package org.example.voice.course.domain.model;

import org.example.voice.course.domain.type.CourseProgressStatus;

import java.time.OffsetDateTime;

public record CourseProgressData(
        Long courseId,
        CourseProgressStatus status,
        Long lastStepId,
        Double progressPercent,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
}
