package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseSummaryData;

public record CourseListItemDto(
        Long id,
        String courseType,
        String title,
        String difficulty,
        Integer estimatedMinutes,
        Double progressPercent
) {

    public static CourseListItemDto from(CourseSummaryData data) {
        return new CourseListItemDto(
                data.id(),
                data.courseType().name(),
                data.title(),
                data.difficulty().name(),
                data.estimatedMinutes(),
                data.progressPercent()
        );
    }
}
