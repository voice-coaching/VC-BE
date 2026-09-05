package org.example.voice.training.domain.entity;

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
import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.type.TrainingSessionStatus;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "training_sessions")
public class TrainingSession {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private PracticeContent content;

    @Column(name = "course_step_id")
    private Long courseStepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_focus", nullable = false)
    private LearningFocus learningFocus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TrainingSessionStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "total_learning_seconds", nullable = false)
    private Integer totalLearningSeconds;

    @Column(name = "failure_reason")
    private String failureReason;

    private TrainingSession(Long userId, PracticeContent content, Long courseStepId, LearningFocus learningFocus) {
        this.userId = userId;
        this.content = content;
        this.courseStepId = courseStepId;
        this.learningFocus = learningFocus;
        this.status = TrainingSessionStatus.RECORDING;
        this.startedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
        this.totalLearningSeconds = 0;
    }

    public static TrainingSession create(Long userId, PracticeContent content, Long courseStepId, LearningFocus learningFocus) {
        return new TrainingSession(userId, content, courseStepId, learningFocus);
    }

    public boolean beginUpload() {
        if (status == TrainingSessionStatus.RECORDING) {
            status = TrainingSessionStatus.UPLOADING;
            return true;
        }
        return status == TrainingSessionStatus.UPLOADING;
    }

    public boolean allowsRecordingChanges() {
        return status == TrainingSessionStatus.RECORDING || status == TrainingSessionStatus.UPLOADING;
    }

    public boolean startAnalysis() {
        if (!allowsRecordingChanges()) {
            return false;
        }
        status = TrainingSessionStatus.ANALYZING;
        return true;
    }

    public boolean allowsAnalysisRetry() {
        return status == TrainingSessionStatus.ANALYZING;
    }

    public void complete(Integer totalLearningSeconds) {
        this.status = TrainingSessionStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
        this.totalLearningSeconds = totalLearningSeconds == null ? 0 : totalLearningSeconds;
    }

    public void cancel() {
        this.status = TrainingSessionStatus.CANCELED;
        this.completedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
    }
}
