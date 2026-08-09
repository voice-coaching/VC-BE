package org.example.voice.course.domain.model;

import org.example.voice.course.domain.type.CourseProgressStatus;

import java.time.OffsetDateTime;

public record CourseProgressItemData(
        Long courseId,
        String title,
        CourseProgressStatus status,
        Long lastStepId,
        Double progressPercent,
        OffsetDateTime updatedAt
) {
}
