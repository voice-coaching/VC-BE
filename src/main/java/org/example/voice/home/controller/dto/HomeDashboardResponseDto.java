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

    public record HomeRecommendationItemDto(
            Long contentId,
            String title,
            String contentType,
            String reason
    ) {

        public static HomeRecommendationItemDto from(RecommendationItemData data) {
            return new HomeRecommendationItemDto(
                    data.contentId(),
                    data.title(),
                    data.contentType().name(),
                    data.reason()
            );
        }
    }

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

    public record HomeCourseProgressDto(
            Long courseId,
            String title,
            Double progressPercent
    ) {

        public static HomeCourseProgressDto from(CourseProgressData data) {
            return new HomeCourseProgressDto(
                    data.courseId(),
                    data.title(),
                    data.progressPercent()
            );
        }
    }
}
