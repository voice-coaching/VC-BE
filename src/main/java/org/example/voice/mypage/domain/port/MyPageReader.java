package org.example.voice.mypage.domain.port;

import org.example.voice.mypage.domain.model.MyPageData;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.training.domain.type.TrainingSessionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface MyPageReader {
    MyPageData.HistoryPage findHistory(Long userId, ContentType type, TrainingSessionStatus status,
                                       OffsetDateTime from, OffsetDateTime to, int page, int size);
    boolean sessionExists(Long sessionId);
    boolean sessionOwned(Long userId, Long sessionId);
    Optional<MyPageData.HistoryDetail> findHistoryDetail(Long userId, Long sessionId);
    MyPageData.Statistics calculateStatistics(Long userId, OffsetDateTime from, OffsetDateTime to,
                                              OffsetDateTime todayFrom, OffsetDateTime todayTo);
    MyPageData.UnitScoreList findUnitScores(Long userId, OffsetDateTime from, OffsetDateTime to);
    MyPageData.TrendPointList findScoreTrend(Long userId, String metric, OffsetDateTime from, OffsetDateTime to);
    MyPageData.RecommendationList findRecommendations(List<String> targetUnits, ContentType contentType, int limit);
}
