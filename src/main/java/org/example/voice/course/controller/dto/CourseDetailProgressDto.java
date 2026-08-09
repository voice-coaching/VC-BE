package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.model.CourseProgressSummaryData;

public record CourseDetailProgressDto(
        String status,
        Double progressPercent,
        Long lastStepId
) {

    public static CourseDetailProgressDto from(CourseProgressSummaryData data) {
        return new CourseDetailProgressDto(
                data.status().name(),
                data.progressPercent(),
                data.lastStepId()
        );
    }
}
