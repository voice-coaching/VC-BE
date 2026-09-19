package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseProgressItemData;

import java.time.OffsetDateTime;

public record CourseProgressListItemDto(
        Long courseId,
        String title,
        String status,
        Long lastStepId,
        Double progressPercent,
        OffsetDateTime updatedAt
) {

    public static CourseProgressListItemDto from(CourseProgressItemData data) {
        return new CourseProgressListItemDto(
                data.courseId(),
                data.title(),
                data.status().name(),
                data.lastStepId(),
                data.progressPercent(),
                data.updatedAt()
        );
    }
}
