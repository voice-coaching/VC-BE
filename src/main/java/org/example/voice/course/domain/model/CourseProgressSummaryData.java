package org.example.voice.course.domain.model;

import org.example.voice.course.domain.type.CourseProgressStatus;

public record CourseProgressSummaryData(
        CourseProgressStatus status,
        Double progressPercent,
        Long lastStepId
) {
}
