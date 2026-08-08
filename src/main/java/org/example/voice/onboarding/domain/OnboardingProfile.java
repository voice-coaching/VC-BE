package org.example.voice.onboarding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.onboarding.controller.dto.SurveyAnswersUpdateRequestDto;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "onboarding_profiles")
public class OnboardingProfile {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_level")
    private CurrentLevel currentLevel;

    @Column(name = "goal_text")
    private String goalText;

    @Column(name = "daily_goal_minutes")
    private Integer dailyGoalMinutes;

    @Column(name = "weekly_goal_count")
    private Integer weeklyGoalCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "survey_answers", columnDefinition = "jsonb", nullable = false)
    private SurveyAnswers surveyAnswers;

    @Column(name = "completed_at", nullable = false)
    private OffsetDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private OnboardingProfile(
            Long userId,
            CurrentLevel currentLevel,
            String goalText,
            Integer dailyGoalMinutes,
            Integer weeklyGoalCount,
            SurveyAnswers surveyAnswers
    ) {
        this.userId = userId;
        update(currentLevel, goalText, dailyGoalMinutes, weeklyGoalCount, surveyAnswers);
    }

    public static OnboardingProfile create(
            Long userId,
            CurrentLevel currentLevel,
            String goalText,
            Integer dailyGoalMinutes,
            Integer weeklyGoalCount,
            SurveyAnswers surveyAnswers
    ) {
        return new OnboardingProfile(userId, currentLevel, goalText, dailyGoalMinutes, weeklyGoalCount, surveyAnswers);
    }

    public void update(
            CurrentLevel currentLevel,
            String goalText,
            Integer dailyGoalMinutes,
            Integer weeklyGoalCount,
            SurveyAnswers surveyAnswers
    ) {
        OffsetDateTime now = OffsetDateTime.now(SEOUL_ZONE_ID);
        this.currentLevel = currentLevel;
        this.goalText = goalText;
        this.dailyGoalMinutes = dailyGoalMinutes;
        this.weeklyGoalCount = weeklyGoalCount;
        this.surveyAnswers = surveyAnswers;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void patch(
            String goalText,
            Integer dailyGoalMinutes,
            Integer weeklyGoalCount,
            SurveyAnswersUpdateRequestDto surveyAnswersUpdateRequest
    ) {
        if (goalText != null) {
            this.goalText = goalText;
        }
        if (dailyGoalMinutes != null) {
            this.dailyGoalMinutes = dailyGoalMinutes;
        }
        if (weeklyGoalCount != null) {
            this.weeklyGoalCount = weeklyGoalCount;
        }
        if (surveyAnswersUpdateRequest != null) {
            this.surveyAnswers.patch(surveyAnswersUpdateRequest);
        }
        this.updatedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
    }
}
