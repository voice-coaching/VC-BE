package org.example.voice.home.controller.dto;

import org.example.voice.home.domain.model.CourseProgressData;

public record HomeCourseProgressDto(
        Long courseId,
        String title,
        Double progressPercent
) {

    public static HomeCourseProgressDto from(CourseProgressData data) {
        return new HomeCourseProgressDto(
                data.courseId(),
                data.title(),
                data.progressPercent()
        );
    }
}
