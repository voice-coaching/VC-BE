package org.example.voice.course.domain.model;

import org.example.voice.course.domain.type.CourseStepType;

public record CourseStepData(
        Long id,
        Integer stepOrder,
        CourseStepType stepType,
        String title,
        Long practiceContentId,
        Boolean completed
) {
}
