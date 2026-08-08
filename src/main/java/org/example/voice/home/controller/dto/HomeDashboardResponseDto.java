package org.example.voice.home.controller.dto;

import org.example.voice.home.domain.model.CourseProgressData;
import org.example.voice.home.domain.model.RecentTrainingData;
import org.example.voice.home.domain.model.RecommendationItemData;
import org.example.voice.home.domain.model.TodayLearningStatusData;

import java.util.List;

public record HomeDashboardResponseDto(
        TodayLearningStatusDto today,
        List<HomeRecommendationItemDto> recommendations,
        HomeRecentTrainingDto recentTraining,
        HomeCourseProgressDto courseProgress
) {

    public static HomeDashboardResponseDto from(
            TodayLearningStatusData today,
            List<RecommendationItemData> recommendations,
            RecentTrainingData recentTraining,
            CourseProgressData courseProgress
    ) {
        return new HomeDashboardResponseDto(
                TodayLearningStatusDto.from(today),
                recommendations.stream()
                        .map(HomeRecommendationItemDto::from)
                        .toList(),
                recentTraining == null ? null : HomeRecentTrainingDto.from(recentTraining),
                courseProgress == null ? null : HomeCourseProgressDto.from(courseProgress)
        );
    }
}
