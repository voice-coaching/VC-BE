package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseProgressData;

public record CourseProgressResponseDto(
        Long courseId,
        String status,
        Long lastStepId,
        Double progressPercent
) {

    public static CourseProgressResponseDto from(CourseProgressData data) {
        return new CourseProgressResponseDto(
                data.courseId(),
                data.status().name(),
                data.lastStepId(),
                data.progressPercent()
        );
    }
}
