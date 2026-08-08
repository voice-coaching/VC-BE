package org.example.voice.home.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.voice.home.domain.model.CourseProgressData;
import org.example.voice.home.domain.port.HomeReader;
import org.example.voice.home.domain.model.RecentTrainingData;
import org.example.voice.home.domain.model.RecommendationItemData;
import org.example.voice.home.domain.model.TodayLearningStatusData;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class HomeReaderImpl implements HomeReader {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_RECOMMENDATION_REASON = "온보딩 응답과 최근 학습 기록을 기준으로 추천합니다.";

    private final EntityManager entityManager;

    @Override
    public TodayLearningStatusData getTodayStatus(Long userId) {
        OffsetDateTime startOfDay = LocalDate.now(SEOUL_ZONE_ID).atStartOfDay(SEOUL_ZONE_ID).toOffsetDateTime();
        OffsetDateTime startOfNextDay = startOfDay.plusDays(1);

        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        select
                            (
                                select count(*)
                                from training_sessions ts
                                where ts.user_id = :userId
                                  and ts.status = 'COMPLETED'
                                  and ts.started_at >= :startOfDay
                                  and ts.started_at < :startOfNextDay
                            ) as completed_count,
                            (
                                select coalesce(sum(ts.total_learning_seconds), 0)
                                from training_sessions ts
                                where ts.user_id = :userId
                                  and ts.started_at >= :startOfDay
                                  and ts.started_at < :startOfNextDay
                            ) as learning_seconds,
                            coalesce((
                                select op.weekly_goal_count
                                from onboarding_profiles op
                                where op.user_id = :userId
                            ), 0) as goal_count
                        """)
                .setParameter("userId", userId)
                .setParameter("startOfDay", startOfDay)
                .setParameter("startOfNextDay", startOfNextDay)
                .getSingleResult();

        return new TodayLearningStatusData(
                toInteger(row[0]),
                toInteger(row[2]),
                toInteger(row[1])
        );
    }

    @Override
    public List<RecommendationItemData> findRecommendations(Long userId, ContentType type, int limit) {
        String typeCondition = type == null ? "" : " and pc.content_type = :type";
        var query = entityManager.createNativeQuery("""
                        select
                            pc.id,
                            pc.content_type,
                            pc.title,
                            pc.difficulty
                        from practice_contents pc
                        where pc.status = 'PUBLISHED'
                        %s
                        order by pc.published_at desc nulls last, pc.created_at desc
                        limit :limit
                        """.formatted(typeCondition))
                .setParameter("limit", limit);

        if (type != null) {
            query.setParameter("type", type.name());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new RecommendationItemData(
                        toLong(row[0]),
                        ContentType.valueOf((String) row[1]),
                        (String) row[2],
                        Difficulty.valueOf((String) row[3]),
                        DEFAULT_RECOMMENDATION_REASON
                ))
                .toList();
    }

    @Override
    public Optional<RecentTrainingData> findRecentTraining(Long userId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select
                            ts.id,
                            ts.content_id,
                            pc.title,
                            ts.status,
                            coalesce(ts.completed_at, ts.started_at) as last_updated_at
                        from training_sessions ts
                        join practice_contents pc on pc.id = ts.content_id
                        where ts.user_id = :userId
                        order by coalesce(ts.completed_at, ts.started_at) desc
                        limit 1
                        """)
                .setParameter("userId", userId)
                .getResultList();

        return rows.stream()
                .findFirst()
                .map(row -> new RecentTrainingData(
                        toLong(row[0]),
                        toLong(row[1]),
                        (String) row[2],
                        TrainingSessionStatus.valueOf((String) row[3]),
                        resolveResumeType((String) row[3]),
                        toOffsetDateTime(row[4])
                ));
    }

    @Override
    public Optional<CourseProgressData> findCourseProgress(Long userId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select
                            c.id,
                            c.title,
                            ucp.progress_percent
                        from user_course_progress ucp
                        join courses c on c.id = ucp.course_id
                        where ucp.user_id = :userId
                        order by ucp.updated_at desc
                        limit 1
                        """)
                .setParameter("userId", userId)
                .getResultList();

        return rows.stream()
                .findFirst()
                .map(row -> new CourseProgressData(
                        toLong(row[0]),
                        (String) row[1],
                        ((Number) row[2]).doubleValue()
                ));
    }

    private String resolveResumeType(String status) {
        if ("ANALYZING".equals(status)) {
            return "ANALYSIS_STATUS";
        }
        if ("COMPLETED".equals(status)) {
            return "ANALYSIS_RESULT";
        }
        return "TRAINING_SESSION";
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Integer toInteger(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atZone(SEOUL_ZONE_ID).toOffsetDateTime();
        }
        return null;
    }
}
