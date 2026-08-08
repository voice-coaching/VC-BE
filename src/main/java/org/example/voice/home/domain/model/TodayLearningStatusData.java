package org.example.voice.home.domain.model;

public record TodayLearningStatusData(
        Integer completedCount,
        Integer goalCount,
        Integer learningSeconds
) {
}
