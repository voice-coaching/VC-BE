package org.example.voice.course.domain.model;

import org.example.voice.course.domain.type.CourseType;
import org.example.voice.practicecontent.domain.type.Difficulty;

public record CourseSummaryData(
        Long id,
        CourseType courseType,
        String title,
        Difficulty difficulty,
        Integer estimatedMinutes,
        Double progressPercent
) {
}
