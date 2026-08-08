package org.example.voice.home.domain.port;

import org.example.voice.home.domain.model.CourseProgressData;
import org.example.voice.home.domain.model.RecentTrainingData;
import org.example.voice.home.domain.model.RecommendationItemData;
import org.example.voice.home.domain.model.TodayLearningStatusData;
import org.example.voice.practicecontent.domain.type.ContentType;

import java.util.List;
import java.util.Optional;

public interface HomeReader {

    TodayLearningStatusData getTodayStatus(Long userId);

    List<RecommendationItemData> findRecommendations(Long userId, ContentType type, int limit);

    Optional<RecentTrainingData> findRecentTraining(Long userId);

    Optional<CourseProgressData> findCourseProgress(Long userId);
}
