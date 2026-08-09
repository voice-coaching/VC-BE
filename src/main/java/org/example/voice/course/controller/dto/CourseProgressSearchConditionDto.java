package org.example.voice.course.controller.dto;

import org.example.voice.course.domain.type.CourseProgressStatus;

public record CourseProgressSearchConditionDto(
        CourseProgressStatus status
) {
}
