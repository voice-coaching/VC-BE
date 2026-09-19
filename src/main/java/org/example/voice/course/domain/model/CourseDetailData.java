package org.example.voice.course.domain.model;

import org.example.voice.course.domain.type.CourseType;
import org.example.voice.practicecontent.domain.type.Difficulty;

public record CourseDetailData(
        Long id,
        CourseType courseType,
        String title,
        String description,
        Difficulty difficulty,
        Integer estimatedMinutes,
        Integer stepCount,
        CourseProgressSummaryData progress
) {
}
