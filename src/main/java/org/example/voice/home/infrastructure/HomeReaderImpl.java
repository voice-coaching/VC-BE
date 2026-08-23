package org.example.voice.home.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.course.infrastructure.UserCourseProgressJpaRepository;
import org.example.voice.home.domain.model.CourseProgressData;
import org.example.voice.home.domain.model.RecentTrainingData;
import org.example.voice.home.domain.model.RecommendationItemData;
import org.example.voice.home.domain.model.TodayLearningStatusData;
import org.example.voice.home.domain.port.HomeReader;
import org.example.voice.home.infrastructure.cache.HomeCacheNames;
import org.example.voice.onboarding.domain.port.OnboardingProfileReader;
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.domain.type.Difficulty;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.example.voice.practicecontent.infrastructure.PracticeContentJpaRepository;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.example.voice.training.infrastructure.TrainingSessionJpaRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeReaderImpl implements HomeReader {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_RECOMMENDATION_REASON = "온보딩 응답과 최근 학습 기록을 기준으로 추천합니다.";

    private final TrainingSessionJpaRepository trainingSessionJpaRepository;
    private final PracticeContentJpaRepository practiceContentJpaRepository;
    private final UserCourseProgressJpaRepository userCourseProgressJpaRepository;
    private final OnboardingProfileReader onboardingProfileReader;

    @Override
    @Cacheable(
            cacheNames = HomeCacheNames.TODAY_STATUS,
            key = "T(org.example.voice.home.infrastructure.cache.HomeCacheKeys).user(#p0)"
    )
    public TodayLearningStatusData getTodayStatus(Long userId) {
        OffsetDateTime startOfDay = LocalDate.now(SEOUL_ZONE_ID).atStartOfDay(SEOUL_ZONE_ID).toOffsetDateTime();
        OffsetDateTime startOfNextDay = startOfDay.plusDays(1);

        int completedCount = trainingSessionJpaRepository
                .countByUserIdAndStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        userId,
                        TrainingSessionStatus.COMPLETED,
                        startOfDay,
                        startOfNextDay
                );
        Integer learningSeconds = trainingSessionJpaRepository.sumLearningSeconds(userId, startOfDay, startOfNextDay);
        Integer goalCount = onboardingProfileReader.findByUserId(userId)
                .map(profile -> profile.getWeeklyGoalCount() == null ? 0 : profile.getWeeklyGoalCount())
                .orElse(0);

        return new TodayLearningStatusData(completedCount, goalCount, learningSeconds == null ? 0 : learningSeconds);
    }

    @Override
    @Cacheable(
            cacheNames = HomeCacheNames.RECOMMENDATIONS,
            key = "T(org.example.voice.home.infrastructure.cache.HomeCacheKeys).recommendations(#p0, #p1, #p2)"
    )
    public List<RecommendationItemData> findRecommendations(Long userId, ContentType type, int limit) {
        PageRequest pageRequest = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Order.desc("publishedAt").nullsLast(), Sort.Order.desc("createdAt"))
        );
        return practiceContentJpaRepository.findAll(recommendationSpec(type), pageRequest)
                .getContent()
                .stream()
                .map(this::toRecommendationData)
                .toList();
    }

    @Override
    @Cacheable(
            cacheNames = HomeCacheNames.RECENT_TRAINING,
            key = "T(org.example.voice.home.infrastructure.cache.HomeCacheKeys).user(#p0)",
            unless = "#result.isEmpty()"
    )
    public Optional<RecentTrainingData> findRecentTraining(Long userId) {
        return trainingSessionJpaRepository.findLatestByUserId(userId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::toRecentTrainingData);
    }

    @Override
    @Cacheable(
            cacheNames = HomeCacheNames.COURSE_PROGRESS,
            key = "T(org.example.voice.home.infrastructure.cache.HomeCacheKeys).user(#p0)",
            unless = "#result.isEmpty()"
    )
    public Optional<CourseProgressData> findCourseProgress(Long userId) {
        return userCourseProgressJpaRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                .map(progress -> new CourseProgressData(
                        progress.getCourse().getId(),
                        progress.getCourse().getTitle(),
                        progress.getProgressPercent().doubleValue()
                ));
    }

    private Specification<PracticeContent> recommendationSpec(ContentType type) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.equal(root.get("status"), PublishStatus.PUBLISHED);
            if (type != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("contentType"), type));
            }
            return predicate;
        };
    }

    private RecommendationItemData toRecommendationData(PracticeContent content) {
        Difficulty difficulty = content.getDifficulty();
        return new RecommendationItemData(
                content.getId(),
                content.getContentType(),
                content.getTitle(),
                difficulty,
                DEFAULT_RECOMMENDATION_REASON
        );
    }

    private RecentTrainingData toRecentTrainingData(TrainingSession session) {
        return new RecentTrainingData(
                session.getId(),
                session.getContent().getId(),
                session.getContent().getTitle(),
                session.getStatus(),
                resolveResumeType(session.getStatus()),
                session.getCompletedAt() == null ? session.getStartedAt() : session.getCompletedAt()
        );
    }

    private String resolveResumeType(TrainingSessionStatus status) {
        if (status == TrainingSessionStatus.ANALYZING) {
            return "ANALYSIS_STATUS";
        }
        if (status == TrainingSessionStatus.COMPLETED) {
            return "ANALYSIS_RESULT";
        }
        return "TRAINING_SESSION";
    }
}
