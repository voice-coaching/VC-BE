package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseDetailData;

public record CourseDetailResponseDto(
        Long id,
        String courseType,
        String title,
        String description,
        String difficulty,
        Integer estimatedMinutes,
        Integer stepCount,
        CourseDetailProgressDto progress
) {

    public static CourseDetailResponseDto from(CourseDetailData data) {
        return new CourseDetailResponseDto(
                data.id(),
                data.courseType().name(),
                data.title(),
                data.description(),
                data.difficulty().name(),
                data.estimatedMinutes(),
                data.stepCount(),
                CourseDetailProgressDto.from(data.progress())
        );
    }
}
