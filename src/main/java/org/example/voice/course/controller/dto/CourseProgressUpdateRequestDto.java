package org.example.voice.course.controller.dto;

public record CourseProgressUpdateRequestDto(
        Long lastStepId,
        Double progressPercent
) {
}
