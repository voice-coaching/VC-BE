package org.example.voice.home.controller.dto;

import org.example.voice.home.domain.model.RecentTrainingData;

public record HomeRecentTrainingDto(
        Long sessionId,
        Long contentId,
        String title,
        String status
) {

    public static HomeRecentTrainingDto from(RecentTrainingData data) {
        return new HomeRecentTrainingDto(
                data.sessionId(),
                data.contentId(),
                data.contentTitle(),
                data.status().name()
        );
    }
}
