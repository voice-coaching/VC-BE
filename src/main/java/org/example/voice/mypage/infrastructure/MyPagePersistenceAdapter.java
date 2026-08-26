package org.example.voice.mypage.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.course.domain.entity.Course;
import org.example.voice.course.infrastructure.CourseJpaRepository;
import org.example.voice.home.infrastructure.cache.HomeCacheNames;
import org.example.voice.mypage.domain.model.MyPageData;
import org.example.voice.mypage.domain.port.MyPageReader;
import org.example.voice.mypage.domain.port.MyPageWriter;
import org.example.voice.mypage.infrastructure.cache.MyPageCacheNames;
import org.example.voice.onboarding.domain.port.OnboardingProfileReader;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.example.voice.practicecontent.infrastructure.PracticeContentJpaRepository;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class MyPagePersistenceAdapter implements MyPageReader, MyPageWriter {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String REASON_SUFFIX = " 발음에서 반복된 약점을 보완하기 위한 추천입니다.";
    @PersistenceContext private EntityManager entityManager;
    private final OnboardingProfileReader onboardingProfileReader;
    private final PracticeContentJpaRepository contentRepository;
    private final CourseJpaRepository courseRepository;

    public MyPagePersistenceAdapter(OnboardingProfileReader onboardingProfileReader,
                                    PracticeContentJpaRepository contentRepository,
                                    CourseJpaRepository courseRepository) {
        this.onboardingProfileReader = onboardingProfileReader;
        this.contentRepository = contentRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Cacheable(
            cacheNames = MyPageCacheNames.HISTORY,
            key = "T(org.example.voice.mypage.infrastructure.cache.MyPageCacheKeys)"
                    + ".history(#p0, #p1, #p2, #p3, #p4, #p5, #p6)"
    )
    public MyPageData.HistoryPage findHistory(Long userId, ContentType type, TrainingSessionStatus status,
                                              OffsetDateTime from, OffsetDateTime to, int page, int size) {
        TrainingSessionStatus selectedStatus = status == null ? TrainingSessionStatus.COMPLETED : status;
        String where = " where s.userId=:userId and s.status=:status and s.completedAt>=:from and s.completedAt<:to "
                + (type == null ? "" : " and s.content.contentType=:type ");
        TypedQuery<Object[]> query = entityManager.createQuery("""
                select s.id, c.id, c.contentType, c.title, s.status, a.overallScore, s.completedAt
                from TrainingSession s join s.content c
                left join VoiceRecording r on r.trainingSession=s and r.selected=true and r.deletedAt is null
                left join AnalysisResult a on a.recording=r and a.status=:analysisStatus
                """ + where + " order by s.completedAt desc, s.id desc", Object[].class);
        bindHistory(query, userId, selectedStatus, type, from, to);
        query.setFirstResult(page * size).setMaxResults(size);
        TypedQuery<Long> count = entityManager.createQuery("select count(s.id) from TrainingSession s" + where, Long.class);
        bindHistory(count, userId, selectedStatus, type, from, to);
        long total = count.getSingleResult();
        List<MyPageData.HistoryItem> items = query.getResultList().stream().map(row -> new MyPageData.HistoryItem(
                (Long) row[0], (Long) row[1], row[2].toString(), (String) row[3], row[4].toString(),
                round((BigDecimal) row[5]), (OffsetDateTime) row[6])).toList();
        int pages = (int) Math.ceil((double) total / size);
        return new MyPageData.HistoryPage(items, page, size, total, pages, page + 1 < pages);
    }

    private void bindHistory(jakarta.persistence.Query query, Long userId, TrainingSessionStatus status,
                             ContentType type, OffsetDateTime from, OffsetDateTime to) {
        query.setParameter("userId", userId).setParameter("status", status)
                .setParameter("from", from).setParameter("to", to);
        if (query.getParameters().stream().anyMatch(it -> "analysisStatus".equals(it.getName())))
            query.setParameter("analysisStatus", AnalysisStatus.COMPLETED);
        if (type != null) query.setParameter("type", type);
    }

    @Override public boolean sessionExists(Long sessionId) {
        return entityManager.createQuery("select count(s.id) from TrainingSession s where s.id=:id", Long.class)
                .setParameter("id", sessionId).getSingleResult() > 0;
    }

    @Override public boolean sessionOwned(Long userId, Long sessionId) {
        return entityManager.createQuery("select count(s.id) from TrainingSession s where s.id=:id and s.userId=:userId", Long.class)
                .setParameter("id", sessionId).setParameter("userId", userId).getSingleResult() > 0;
    }

    @Override
    @Cacheable(
            cacheNames = MyPageCacheNames.HISTORY_DETAIL,
            key = "T(org.example.voice.mypage.infrastructure.cache.MyPageCacheKeys).historyDetail(#p0, #p1)",
            unless = "#result == null"
    )
    public Optional<MyPageData.HistoryDetail> findHistoryDetail(Long userId, Long sessionId) {
        List<Object[]> sessions = entityManager.createQuery("""
                select s.id, s.status, s.startedAt, s.completedAt, s.totalLearningSeconds,
                       c.id, c.title, c.scriptText
                from TrainingSession s join s.content c where s.id=:id and s.userId=:userId
                """, Object[].class).setParameter("id", sessionId).setParameter("userId", userId).getResultList();
        if (sessions.isEmpty()) return Optional.empty();
        Object[] session = sessions.getFirst();
        List<Object[]> analyses = entityManager.createQuery("""
                select r.id, r.durationMs, r.qualityStatus, a.id, a.transcript, a.overallScore
                from VoiceRecording r join AnalysisResult a on a.recording=r
                where r.trainingSession.id=:id and r.selected=true and r.deletedAt is null
                  and a.status=:status order by a.createdAt desc, a.id desc
                """, Object[].class).setParameter("id", sessionId).setParameter("status", AnalysisStatus.COMPLETED)
                .setMaxResults(1).getResultList();
        if (analyses.isEmpty()) return Optional.empty();
        Object[] analysis = analyses.getFirst();
        List<MyPageData.Segment> segments = entityManager.createQuery("""
                select s.sequenceNo, s.expectedText, s.recognizedText, s.startMs, s.endMs, s.resultStatus
                from AnalysisSegment s where s.analysisResult.id=:id order by s.sequenceNo, s.id
                """, Object[].class).setParameter("id", analysis[3]).getResultList().stream().map(row ->
                new MyPageData.Segment((Integer) row[0], (String) row[1], (String) row[2], (Integer) row[3],
                        (Integer) row[4], row[5].toString())).toList();
        return Optional.of(new MyPageData.HistoryDetail(
                new MyPageData.Session((Long) session[0], session[1].toString(), (OffsetDateTime) session[2],
                        (OffsetDateTime) session[3], (Integer) session[4]),
                new MyPageData.Content((Long) session[5], (String) session[6], (String) session[7]),
                new MyPageData.Recording((Long) analysis[0], (Integer) analysis[1], analysis[2].toString()),
                new MyPageData.Analysis((Long) analysis[3], (String) analysis[4], round((BigDecimal) analysis[5])), segments));
    }

    @Override
    @Cacheable(
            cacheNames = MyPageCacheNames.STATISTICS,
            key = "T(org.example.voice.mypage.infrastructure.cache.MyPageCacheKeys)"
                    + ".statistics(#p0, #p1, #p2, #p3, #p4)"
    )
    public MyPageData.Statistics calculateStatistics(Long userId, OffsetDateTime from, OffsetDateTime to,
                                                     OffsetDateTime todayFrom, OffsetDateTime todayTo) {
        Object[] totals = entityManager.createQuery("""
                select count(s.id), coalesce(sum(s.totalLearningSeconds),0)
                from TrainingSession s
                where s.userId=:userId and s.status=:status and s.completedAt>=:from and s.completedAt<:to
                """, Object[].class).setParameter("userId", userId).setParameter("status", TrainingSessionStatus.COMPLETED)
                .setParameter("from", from).setParameter("to", to).getSingleResult();
        Object[] averages = entityManager.createQuery("""
                select avg(a.overallScore), avg(a.pronunciationScore), avg(a.intonationScore)
                from AnalysisResult a where a.recording.trainingSession.userId=:userId
                  and a.recording.selected=true and a.recording.deletedAt is null and a.status=:status
                  and a.recording.trainingSession.completedAt>=:from and a.recording.trainingSession.completedAt<:to
                """, Object[].class).setParameter("userId", userId).setParameter("status", AnalysisStatus.COMPLETED)
                .setParameter("from", from).setParameter("to", to).getSingleResult();
        Long todayCount = entityManager.createQuery("""
                select count(s.id) from TrainingSession s where s.userId=:userId and s.status=:status
                and s.completedAt>=:from and s.completedAt<:to
                """, Long.class).setParameter("userId", userId).setParameter("status", TrainingSessionStatus.COMPLETED)
                .setParameter("from", todayFrom).setParameter("to", todayTo).getSingleResult();
        List<OffsetDateTime> completed = entityManager.createQuery("""
                select s.completedAt from TrainingSession s where s.userId=:userId and s.status=:status
                and s.completedAt is not null order by s.completedAt desc
                """, OffsetDateTime.class).setParameter("userId", userId)
                .setParameter("status", TrainingSessionStatus.COMPLETED).getResultList();
        int goal = onboardingProfileReader.findByUserId(userId).map(it ->
                it.getWeeklyGoalCount() == null ? 0 : it.getWeeklyGoalCount()).orElse(0);
        return new MyPageData.Statistics(new MyPageData.DateRange(from.atZoneSameInstant(SEOUL).toLocalDate(),
                to.minusNanos(1).atZoneSameInstant(SEOUL).toLocalDate()), ((Long) totals[0]).intValue(),
                ((Number) totals[1]).intValue(), todayCount.intValue(), goal, consecutiveDays(completed),
                round(number(averages[0])), round(number(averages[1])), round(number(averages[2])));
    }

    @Override
    @Cacheable(
            cacheNames = MyPageCacheNames.UNIT_SCORES,
            key = "T(org.example.voice.mypage.infrastructure.cache.MyPageCacheKeys).unitScores(#p0, #p1, #p2)"
    )
    public MyPageData.UnitScoreList findUnitScores(Long userId, OffsetDateTime from, OffsetDateTime to) {
        List<Object[]> rows = entityManager.createQuery("""
                select s.targetUnit, avg(s.pronunciationScore), count(s.id)
                from AnalysisSegment s where s.targetUnit is not null and s.pronunciationScore is not null
                and s.analysisResult.status=:status and s.analysisResult.recording.trainingSession.userId=:userId
                and s.analysisResult.analyzedAt>=:from and s.analysisResult.analyzedAt<:to
                group by s.targetUnit
                """, Object[].class).setParameter("status", AnalysisStatus.COMPLETED).setParameter("userId", userId)
                .setParameter("from", from).setParameter("to", to).getResultList();
        List<MyPageData.UnitScore> items = rows.stream().map(row -> {
            String target = (String) row[0];
            String error = mostCommonError(userId, target, from, to);
            return new MyPageData.UnitScore(target, label(target), round(number(row[1])), ((Long) row[2]).intValue(), error);
        }).toList();
        return new MyPageData.UnitScoreList(items);
    }

    private String mostCommonError(Long userId, String target, OffsetDateTime from, OffsetDateTime to) {
        List<Object[]> rows = entityManager.createQuery("""
                select s.errorType, count(s.id) from AnalysisSegment s
                where s.targetUnit=:target and s.errorType is not null and s.analysisResult.status=:status
                and s.analysisResult.recording.trainingSession.userId=:userId
                and s.analysisResult.analyzedAt>=:from and s.analysisResult.analyzedAt<:to
                group by s.errorType order by count(s.id) desc
                """, Object[].class).setParameter("target", target).setParameter("status", AnalysisStatus.COMPLETED)
                .setParameter("userId", userId).setParameter("from", from).setParameter("to", to)
                .setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : (String) rows.getFirst()[0];
    }

    @Override
    @Cacheable(
            cacheNames = MyPageCacheNames.SCORE_TREND,
            key = "T(org.example.voice.mypage.infrastructure.cache.MyPageCacheKeys)"
                    + ".scoreTrend(#p0, #p1, #p2, #p3)"
    )
    public MyPageData.TrendPointList findScoreTrend(Long userId, String metric, OffsetDateTime from, OffsetDateTime to) {
        String field = switch (metric) { case "OVERALL" -> "a.overallScore"; case "PRONUNCIATION" -> "a.pronunciationScore";
            case "INTONATION" -> "a.intonationScore"; default -> throw new IllegalArgumentException(metric); };
        List<Object[]> rows = entityManager.createQuery("select a.analyzedAt, " + field + " from AnalysisResult a "
                + "where a.recording.trainingSession.userId=:userId and a.status=:status and a.analyzedAt>=:from "
                + "and a.analyzedAt<:to and " + field + " is not null order by a.analyzedAt", Object[].class)
                .setParameter("userId", userId).setParameter("status", AnalysisStatus.COMPLETED)
                .setParameter("from", from).setParameter("to", to).getResultList();
        Map<LocalDate, List<BigDecimal>> grouped = rows.stream().collect(Collectors.groupingBy(
                row -> ((OffsetDateTime) row[0]).atZoneSameInstant(SEOUL).toLocalDate(), LinkedHashMap::new,
                Collectors.mapping(row -> number(row[1]), Collectors.toList())));
        List<MyPageData.TrendPoint> items = grouped.entrySet().stream().map(entry -> new MyPageData.TrendPoint(entry.getKey(), round(entry.getValue()
                .stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(entry.getValue().size()),
                        2, RoundingMode.HALF_UP)), entry.getValue().size())).toList();
        return new MyPageData.TrendPointList(items);
    }

    @Override
    @Cacheable(
            cacheNames = MyPageCacheNames.RECOMMENDATIONS,
            key = "T(org.example.voice.mypage.infrastructure.cache.MyPageCacheKeys)"
                    + ".recommendations(#p0, #p1, #p2)"
    )
    public MyPageData.RecommendationList findRecommendations(List<String> targetUnits, ContentType contentType, int limit) {
        if (targetUnits.isEmpty()) return new MyPageData.RecommendationList(List.of());
        List<MyPageData.Recommendation> result = new ArrayList<>();
        int candidateLimit = Math.max(50, limit * 10);
        List<PracticeContent> contents = contentRepository
                .findByStatusOrderByPublishedAtDescCreatedAtDesc(PublishStatus.PUBLISHED, PageRequest.of(0, candidateLimit)).stream()
                .filter(it -> contentType == null || it.getContentType() == contentType)
                .filter(it -> it.getTargetPronunciations() != null && it.getTargetPronunciations().stream().anyMatch(targetUnits::contains))
                .toList();
        for (PracticeContent content : contents) {
            if (result.size() == limit) break;
            String target = content.getTargetPronunciations().stream().filter(targetUnits::contains).findFirst().orElse(targetUnits.getFirst());
            result.add(new MyPageData.Recommendation("CONTENT", content.getId(), null, content.getContentType().name(),
                    content.getTitle(), label(target) + REASON_SUFFIX));
        }
        if (contentType == null) for (Course course : courseRepository
                .findByStatusOrderByUpdatedAtDesc(PublishStatus.PUBLISHED, PageRequest.of(0, limit))) {
            if (result.size() == limit) break;
            result.add(new MyPageData.Recommendation("COURSE", null, course.getId(), null, course.getTitle(),
                    "발음 원리부터 단계적으로 약점을 복습할 수 있습니다."));
        }
        return new MyPageData.RecommendationList(result);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = HomeCacheNames.TODAY_STATUS, allEntries = true),
            @CacheEvict(cacheNames = HomeCacheNames.RECENT_TRAINING, allEntries = true),
            @CacheEvict(cacheNames = {
                    MyPageCacheNames.HISTORY,
                    MyPageCacheNames.HISTORY_DETAIL,
                    MyPageCacheNames.STATISTICS,
                    MyPageCacheNames.UNIT_SCORES,
                    MyPageCacheNames.SCORE_TREND,
                    MyPageCacheNames.RECOMMENDATIONS
            }, allEntries = true)
    })
    public void deleteHistory(Long sessionId) {
        entityManager.createQuery("delete from AnalysisSegment s where s.analysisResult.id in (select a.id from AnalysisResult a where a.recording.trainingSession.id=:id)")
                .setParameter("id", sessionId).executeUpdate();
        entityManager.createQuery("delete from AnalysisResult a where a.recording.trainingSession.id=:id")
                .setParameter("id", sessionId).executeUpdate();
        entityManager.createQuery("delete from VoiceRecording r where r.trainingSession.id=:id")
                .setParameter("id", sessionId).executeUpdate();
        entityManager.createQuery("delete from TrainingSession s where s.id=:id")
                .setParameter("id", sessionId).executeUpdate();
    }

    private int consecutiveDays(List<OffsetDateTime> times) {
        var dates = times.stream().map(it -> it.atZoneSameInstant(SEOUL).toLocalDate()).distinct().toList();
        LocalDate expected = LocalDate.now(SEOUL); int count = 0;
        if (!dates.isEmpty() && dates.getFirst().equals(expected.minusDays(1))) expected = expected.minusDays(1);
        for (LocalDate date : dates) { if (date.equals(expected)) { count++; expected = expected.minusDays(1); }
            else if (date.isBefore(expected)) break; }
        return count;
    }
    private BigDecimal number(Object value) { return value == null ? null : new BigDecimal(value.toString()); }
    private BigDecimal round(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.setScale(1, RoundingMode.HALF_UP); }
    private String label(String target) {
        return switch (target) {
            case "FINAL_CONSONANT_G" -> "받침 ㄱ";
            case "FINAL_CONSONANT_N" -> "받침 ㄴ";
            case "FINAL_CONSONANT_D" -> "받침 ㄷ";
            case "FINAL_CONSONANT_L" -> "받침 ㄹ";
            case "FINAL_CONSONANT_M" -> "받침 ㅁ";
            case "FINAL_CONSONANT_B" -> "받침 ㅂ";
            case "FINAL_CONSONANT_NG" -> "받침 ㅇ";
            case "TENSE_CONSONANT_GG" -> "된소리 ㄲ";
            case "TENSE_CONSONANT_DD" -> "된소리 ㄸ";
            case "TENSE_CONSONANT_BB" -> "된소리 ㅃ";
            case "TENSE_CONSONANT_SS", "TENSE_SS" -> "된소리 ㅆ";
            case "TENSE_CONSONANT_JJ" -> "된소리 ㅉ";
            default -> target.replace('_', ' ');
        };
    }
}
