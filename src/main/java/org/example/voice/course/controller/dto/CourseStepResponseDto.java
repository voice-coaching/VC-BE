package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseStepListData;

import java.util.List;

public record CourseStepResponseDto(
        List<CourseStepItemDto> items,
        int stepCount
) {

    public static CourseStepResponseDto from(CourseStepListData data) {
        return new CourseStepResponseDto(
                data.items().stream()
                        .map(CourseStepItemDto::from)
                        .toList(),
                data.items().size()
        );
    }
}
