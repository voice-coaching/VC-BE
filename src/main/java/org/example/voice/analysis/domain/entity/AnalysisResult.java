package org.example.voice.analysis.domain.entity;

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
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;
import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.domain.type.SpeedStatus;
import org.example.voice.training.domain.entity.VoiceRecording;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "analysis_results")
public class AnalysisResult {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recording_id", nullable = false)
    private VoiceRecording recording;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AnalysisStatus status;

    @Column(name = "transcript")
    private String transcript;

    @Column(name = "stt_confidence")
    private BigDecimal sttConfidence;

    @Column(name = "stt_model_name")
    private String sttModelName;

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Column(name = "pronunciation_score")
    private BigDecimal pronunciationScore;

    @Column(name = "intonation_score")
    private BigDecimal intonationScore;

    @Column(name = "speed_wpm")
    private BigDecimal speedWpm;

    @Enumerated(EnumType.STRING)
    @Column(name = "speed_status")
    private SpeedStatus speedStatus;

    @Column(name = "stress_score")
    private BigDecimal stressScore;

    @Column(name = "pause_score")
    private BigDecimal pauseScore;

    @Column(name = "strengths_text")
    private String strengthsText;

    @Column(name = "weaknesses_text")
    private String weaknessesText;

    @Column(name = "summary_feedback")
    private String summaryFeedback;

    @Column(name = "analyzed_at")
    private OffsetDateTime analyzedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "failure_code")
    private String failureCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_outcome")
    private AnalysisOutcome analysisOutcome;

    @Column(name = "active_request_event_id", length = 36)
    private String activeRequestEventId;

    @Column(name = "worker_revision", length = 100)
    private String workerRevision;

    @Column(name = "pipeline_revision", length = 100)
    private String pipelineRevision;

    @Column(name = "audio_sha256", length = 64)
    private String audioSha256;

    @Column(name = "feedback_regeneration_count", nullable = false)
    private Integer feedbackRegenerationCount;

    @Column(name = "feedback_regenerated_at")
    private OffsetDateTime feedbackRegeneratedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private AnalysisResult(VoiceRecording recording, UUID requestEventId) {
        this.recording = recording;
        this.status = AnalysisStatus.PENDING;
        this.activeRequestEventId = requestEventId.toString();
        this.feedbackRegenerationCount = 0;
        this.createdAt = OffsetDateTime.now(SEOUL_ZONE_ID);
        this.analyzedAt = this.createdAt;
    }

    public static AnalysisResult pending(VoiceRecording recording, UUID requestEventId) {
        return new AnalysisResult(recording, requestEventId);
    }

    public void retry(UUID requestEventId) {
        this.status = AnalysisStatus.PENDING;
        this.activeRequestEventId = requestEventId.toString();
        clearWorkerResult();
        this.analyzedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
    }

    public boolean isForActiveRequest(UUID requestEventId) {
        return requestEventId != null && requestEventId.toString().equals(activeRequestEventId);
    }

    public boolean markProcessing() {
        if (status == AnalysisStatus.PENDING) {
            status = AnalysisStatus.PROCESSING;
            analyzedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
            return true;
        }
        return false;
    }

    public boolean complete(AnalysisWorkerResult result) {
        if (status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED) {
            return false;
        }
        this.status = AnalysisStatus.COMPLETED;
        this.analysisOutcome = result.outcome();
        this.transcript = result.transcript();
        this.sttConfidence = result.sttConfidence();
        this.sttModelName = result.sttModelName();
        this.overallScore = result.overallScore();
        this.pronunciationScore = result.pronunciationScore();
        this.intonationScore = result.intonationScore();
        this.speedWpm = result.speedWpm();
        this.speedStatus = result.speedStatus();
        this.stressScore = result.stressScore();
        this.pauseScore = result.pauseScore();
        this.strengthsText = result.strengthsText();
        this.weaknessesText = result.weaknessesText();
        this.summaryFeedback = result.summaryFeedback();
        this.workerRevision = result.workerRevision();
        this.pipelineRevision = result.pipelineRevision();
        this.audioSha256 = result.audioSha256();
        this.failureCode = null;
        this.failureReason = null;
        this.analyzedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
        return true;
    }

    public boolean fail(String code, String reason, String workerRevision, String pipelineRevision) {
        if (status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED) {
            return false;
        }
        clearWorkerResult();
        this.status = AnalysisStatus.FAILED;
        this.failureCode = code;
        this.failureReason = reason;
        this.workerRevision = workerRevision;
        this.pipelineRevision = pipelineRevision;
        this.analyzedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
        return true;
    }

    public OffsetDateTime updatedAt() {
        return analyzedAt == null ? createdAt : analyzedAt;
    }

    public boolean isCompleted() {
        return status == AnalysisStatus.COMPLETED;
    }

    public void regenerateFeedback(
            String strengthsText,
            String weaknessesText,
            String summaryFeedback,
            OffsetDateTime regeneratedAt
    ) {
        this.strengthsText = strengthsText;
        this.weaknessesText = weaknessesText;
        this.summaryFeedback = summaryFeedback;
        this.feedbackRegenerationCount += 1;
        this.feedbackRegeneratedAt = regeneratedAt;
    }

    private void clearWorkerResult() {
        this.transcript = null;
        this.sttConfidence = null;
        this.sttModelName = null;
        this.overallScore = null;
        this.pronunciationScore = null;
        this.intonationScore = null;
        this.speedWpm = null;
        this.speedStatus = null;
        this.stressScore = null;
        this.pauseScore = null;
        this.strengthsText = null;
        this.weaknessesText = null;
        this.summaryFeedback = null;
        this.analysisOutcome = null;
        this.failureCode = null;
        this.failureReason = null;
        this.workerRevision = null;
        this.pipelineRevision = null;
        this.audioSha256 = null;
    }
}
