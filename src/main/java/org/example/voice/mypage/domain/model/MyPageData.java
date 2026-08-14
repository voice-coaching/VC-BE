package org.example.voice.mypage.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class MyPageData {
    private MyPageData() {}

    public record HistoryItem(Long sessionId, Long contentId, String contentType, String title, String status,
                              BigDecimal overallScore, OffsetDateTime completedAt) {}
    public record HistoryPage(List<HistoryItem> items, int page, int size, long totalElements,
                              int totalPages, boolean hasNext) {}
    public record Session(Long id, String status, OffsetDateTime startedAt, OffsetDateTime completedAt,
                          Integer totalLearningSeconds) {}
    public record Content(Long id, String title, String scriptText) {}
    public record Recording(Long id, Integer durationMs, String qualityStatus) {}
    public record Analysis(Long id, String transcript, BigDecimal overallScore) {}
    public record Segment(Integer sequenceNo, String expectedText, String recognizedText, Integer startMs,
                          Integer endMs, String resultStatus) {}
    public record HistoryDetail(Session session, Content content, Recording recording, Analysis analysis,
                                List<Segment> segments) {}
    public record DateRange(LocalDate from, LocalDate to) {}
    public record Statistics(DateRange period, int totalSessionCount, int totalLearningSeconds,
                             int todaySessionCount, int todayGoalCount, int consecutiveLearningDays,
                             BigDecimal averageOverallScore, BigDecimal averagePronunciationScore,
                             BigDecimal averageIntonationScore) {}
    public record UnitScore(String targetUnit, String label, BigDecimal averageScore, int attemptCount,
                            String commonErrorType) {}
    public record StrengthsWeaknesses(List<UnitScore> strengths, List<UnitScore> weaknesses,
                                      boolean minimumDataSatisfied) {}
    public record TrendPoint(LocalDate date, BigDecimal score, int sessionCount) {}
    public record ScoreTrend(String metric, List<TrendPoint> points) {}
    public record Weakness(String targetUnit, String label, BigDecimal averageScore) {}
    public record Recommendation(String targetType, Long contentId, Long courseId, String contentType,
                                 String title, String reason) {}
    public record WeaknessRecommendations(List<Weakness> weaknesses, List<Recommendation> recommendations) {}
}
