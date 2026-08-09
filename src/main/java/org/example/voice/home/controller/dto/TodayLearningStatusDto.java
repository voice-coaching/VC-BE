package org.example.voice.home.controller.dto;

import org.example.voice.home.domain.model.TodayLearningStatusData;

public record TodayLearningStatusDto(
        Integer completedCount,
        Integer goalCount,
        Integer learningSeconds
) {

    public static TodayLearningStatusDto from(TodayLearningStatusData data) {
        return new TodayLearningStatusDto(
                data.completedCount(),
                data.goalCount(),
                data.learningSeconds()
        );
    }
}
