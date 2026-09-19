package org.example.voice.home.domain.model;

public record CourseProgressData(
        Long courseId,
        String title,
        Double progressPercent
) {
}
