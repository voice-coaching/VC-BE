package org.example.voice.course.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.course.domain.type.CourseProgressStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_course_progress")
public class UserCourseProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "last_step_id")
    private Long lastStepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CourseProgressStatus status;

    @Column(name = "progress_percent", nullable = false)
    private BigDecimal progressPercent;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static UserCourseProgress start(Course course, Long userId, Long firstStepId, OffsetDateTime now) {
        UserCourseProgress progress = new UserCourseProgress();
        progress.course = course;
        progress.userId = userId;
        progress.status = CourseProgressStatus.IN_PROGRESS;
        progress.lastStepId = firstStepId;
        progress.progressPercent = BigDecimal.ZERO;
        progress.startedAt = now;
        progress.completedAt = null;
        progress.updatedAt = now;
        return progress;
    }

    public void updateProgress(Long lastStepId, BigDecimal progressPercent, OffsetDateTime now) {
        this.lastStepId = lastStepId;
        this.progressPercent = progressPercent;
        if (this.status == CourseProgressStatus.NOT_STARTED) {
            this.status = CourseProgressStatus.IN_PROGRESS;
        }
        this.updatedAt = now;
    }

    public void complete(OffsetDateTime now) {
        this.status = CourseProgressStatus.COMPLETED;
        this.progressPercent = BigDecimal.valueOf(100.0);
        this.completedAt = now;
        this.updatedAt = now;
    }
}
