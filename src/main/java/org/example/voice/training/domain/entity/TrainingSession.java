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
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;

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

    @Column(name = "user_id", nullable = true)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = true)
    private PracticeContent content;

    @Column(name = "course_step_id")
    private Long courseStepId;

    @Column(name = "course_education_revision_id")
    private Long courseEducationRevisionId;

    @Column(name = "practice_example_id", length = 100) private String practiceExampleId;
    @Column(name = "practice_example_set_id") private Long practiceExampleSetId;
    @Column(name = "practice_example_revision") private Integer practiceExampleRevision;

    public void pinPracticeExample(String id, Long setId, Integer revision) {

        practiceExampleId = id; practiceExampleSetId = setId; practiceExampleRevision = revision;
    }

    public void pinCourseEducation(Long revisionId) {

        courseEducationRevisionId = revisionId;
    }

    @Column(name = "sentence_index") private Integer sentenceIndex;
    @Column(name = "sentence_text_sha256", length = 64) private String sentenceTextSha256;
    @Column(name = "sentence_prompt_revision", length = 128) private String sentencePromptRevision;

    @Column(name = "content_script_snapshot", columnDefinition = "text")
    private String contentScriptSnapshot;
    @Column(name = "content_updated_at_snapshot")
    private OffsetDateTime contentUpdatedAtSnapshot;
    private String sourceScript() { return contentScriptSnapshot != null ? contentScriptSnapshot : content.getScriptText(); }

    public void pinSentence(Integer index, String expectedText) {

        String selected = org.example.voice.training.domain.model.SentenceTranscript.at(sourceScript(), index);

        sentenceIndex = index;
        sentenceTextSha256 = org.example.voice.training.domain.model.SentenceTranscript.sha256(selected);
        sentencePromptRevision = content.getUpdatedAt().toInstant() + ":sentence:" + index;
    }

    public String analysisScriptText() {
        if (sentenceIndex == null) return sourceScript();
        String selected = org.example.voice.training.domain.model.SentenceTranscript.at(sourceScript(), sentenceIndex);

        return selected;
    }

    public String analysisPromptRevision() {
        return sentenceIndex == null ? (contentUpdatedAtSnapshot != null ? contentUpdatedAtSnapshot : content.getUpdatedAt()).toInstant().toString() : sentencePromptRevision;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_focus", nullable = true)
    private LearningFocus learningFocus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = true)
    private TrainingSessionStatus status;

    @Column(name = "started_at", nullable = true)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "total_learning_seconds", nullable = true)
    private Integer totalLearningSeconds;

    @Column(name = "failure_reason")
    private String failureReason;

    private TrainingSession(Long userId, PracticeContent content, Long courseStepId, LearningFocus learningFocus) {
        this.userId = userId;
        this.content = content;
        if (content.getOwnerId() == null) {
            this.contentScriptSnapshot = content.getScriptText();
            this.contentUpdatedAtSnapshot = content.getUpdatedAt();
        }
        this.courseStepId = courseStepId;
        this.learningFocus = learningFocus;
        this.status = TrainingSessionStatus.RECORDING;
        this.startedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
        this.totalLearningSeconds = 0;
    }

    public static TrainingSession create(Long userId, PracticeContent content, Long courseStepId, LearningFocus learningFocus) {
        return new TrainingSession(userId, content, courseStepId, learningFocus);
    }

    public boolean beginUpload() { this.status=TrainingSessionStatus.UPLOADING; return true; }

    public boolean allowsRecordingChanges() { return true; }

    /** Caller must hold the session lock and verify the current canonical non-ACCEPT result. */
    public void beginCanonicalRerecordUpload() {

        status = TrainingSessionStatus.UPLOADING;
    }

    public boolean startAnalysis() {

        status = TrainingSessionStatus.ANALYZING;
        return true;
    }

    public boolean allowsAnalysisRetry() { return true; }

    public void complete(Integer totalLearningSeconds) {

        this.status = TrainingSessionStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
        if (totalLearningSeconds != null) {
            this.totalLearningSeconds = totalLearningSeconds;
        }
    }

    public void cancel() {
        this.status = TrainingSessionStatus.CANCELED;
        this.completedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
    }
}
