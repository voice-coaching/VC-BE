package org.example.voice.mypage.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.voice.mypage.domain.model.MyPageData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class MyPageResponses {
    private MyPageResponses() {}

    public record HistoryItem(Long sessionId, Long contentId, String contentType, String title, String status,
                              BigDecimal overallScore, OffsetDateTime completedAt) {}
    public record HistoryList(List<HistoryItem> items, int page, int size, long totalElements,
                              int totalPages, boolean hasNext) {
        public static HistoryList from(MyPageData.HistoryPage data) {
            return new HistoryList(data.items().stream().map(it -> new HistoryItem(it.sessionId(), it.contentId(),
                    it.contentType(), it.title(), it.status(), it.overallScore(), it.completedAt())).toList(),
                    data.page(), data.size(), data.totalElements(), data.totalPages(), data.hasNext());
        }
    }
    public record Session(Long id, String status, OffsetDateTime startedAt, OffsetDateTime completedAt,
                          Integer totalLearningSeconds) {}
    public record Content(Long id, String title, String scriptText) {}
    public record Recording(Long id, Integer durationMs, String qualityStatus) {}
    public record Analysis(Long id, String transcript, BigDecimal overallScore) {}
    public record Segment(Integer sequenceNo, String expectedText, String recognizedText, Integer startMs,
                          Integer endMs, String resultStatus) {}
    public record HistoryDetail(Session session, Content content, Recording recording, Analysis analysis,
                                List<Segment> segments) {
        public static HistoryDetail from(MyPageData.HistoryDetail data) {
            return new HistoryDetail(new Session(data.session().id(), data.session().status(), data.session().startedAt(),
                    data.session().completedAt(), data.session().totalLearningSeconds()),
                    new Content(data.content().id(), data.content().title(), data.content().scriptText()),
                    new Recording(data.recording().id(), data.recording().durationMs(), data.recording().qualityStatus()),
                    new Analysis(data.analysis().id(), data.analysis().transcript(), data.analysis().overallScore()),
                    data.segments().stream().map(it -> new Segment(it.sequenceNo(), it.expectedText(), it.recognizedText(),
                            it.startMs(), it.endMs(), it.resultStatus())).toList());
        }
    }
    public record Period(LocalDate from, LocalDate to) {}
    public record Statistics(Period period, int totalSessionCount, int totalLearningSeconds, int todaySessionCount,
                             int todayGoalCount, int consecutiveLearningDays, BigDecimal averageOverallScore,
                             BigDecimal averagePronunciationScore, BigDecimal averageIntonationScore) {
        public static Statistics from(MyPageData.Statistics data) {
            return new Statistics(new Period(data.period().from(), data.period().to()), data.totalSessionCount(),
                    data.totalLearningSeconds(), data.todaySessionCount(), data.todayGoalCount(),
                    data.consecutiveLearningDays(), data.averageOverallScore(), data.averagePronunciationScore(),
                    data.averageIntonationScore());
        }
    }
    public record Strength(String targetUnit, String label, BigDecimal averageScore, int attemptCount) {}
    public record WeaknessDetail(String targetUnit, String label, BigDecimal averageScore, int attemptCount,
                                 String commonErrorType) {}
    public record StrengthsWeaknesses(List<Strength> strengths, List<WeaknessDetail> weaknesses,
                                      boolean minimumDataSatisfied) {
        public static StrengthsWeaknesses from(MyPageData.StrengthsWeaknesses data) {
            return new StrengthsWeaknesses(data.strengths().stream().map(it -> new Strength(it.targetUnit(), it.label(),
                    it.averageScore(), it.attemptCount())).toList(), data.weaknesses().stream().map(it ->
                    new WeaknessDetail(it.targetUnit(), it.label(), it.averageScore(), it.attemptCount(),
                            it.commonErrorType())).toList(), data.minimumDataSatisfied());
        }
    }
    public record TrendPoint(LocalDate date, BigDecimal score, int sessionCount) {}
    public record ScoreTrend(String metric, List<TrendPoint> points) {
        public static ScoreTrend from(MyPageData.ScoreTrend data) {
            return new ScoreTrend(data.metric(), data.points().stream().map(it ->
                    new TrendPoint(it.date(), it.score(), it.sessionCount())).toList());
        }
    }
    public record Weakness(String targetUnit, String label, BigDecimal averageScore) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Recommendation(String targetType, Long contentId, Long courseId, String contentType,
                                 String title, String reason) {}
    public record WeaknessRecommendations(List<Weakness> weaknesses, List<Recommendation> recommendations) {
        public static WeaknessRecommendations from(MyPageData.WeaknessRecommendations data) {
            return new WeaknessRecommendations(data.weaknesses().stream().map(it ->
                    new Weakness(it.targetUnit(), it.label(), it.averageScore())).toList(),
                    data.recommendations().stream().map(it -> new Recommendation(it.targetType(), it.contentId(),
                            it.courseId(), it.contentType(), it.title(), it.reason())).toList());
        }
    }
}
