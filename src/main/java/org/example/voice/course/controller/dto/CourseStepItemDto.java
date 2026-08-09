package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseStepData;

public record CourseStepItemDto(
        Long id,
        Integer stepOrder,
        String stepType,
        String title,
        Long practiceContentId,
        Boolean completed
) {

    public static CourseStepItemDto from(CourseStepData data) {
        return new CourseStepItemDto(
                data.id(),
                data.stepOrder(),
                data.stepType().name(),
                data.title(),
                data.practiceContentId(),
                data.completed()
        );
    }
}
