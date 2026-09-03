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
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.domain.type.SpeedStatus;
import org.example.voice.training.domain.entity.VoiceRecording;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

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

    @Column(name = "feedback_regeneration_count", nullable = false)
    private Integer feedbackRegenerationCount;

    @Column(name = "feedback_regenerated_at")
    private OffsetDateTime feedbackRegeneratedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private AnalysisResult(VoiceRecording recording) {
        this.recording = recording;
        this.status = AnalysisStatus.PENDING;
        this.feedbackRegenerationCount = 0;
        this.createdAt = OffsetDateTime.now(SEOUL_ZONE_ID);
        this.analyzedAt = this.createdAt;
    }

    public static AnalysisResult pending(VoiceRecording recording) {
        return new AnalysisResult(recording);
    }

    public void retry() {
        this.status = AnalysisStatus.PENDING;
        this.failureReason = null;
        this.analyzedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
    }

    public void markProcessing() {
        this.status = AnalysisStatus.PROCESSING;
        this.analyzedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
    }

    public void complete(
            String transcript,
            BigDecimal sttConfidence,
            String sttModelName,
            BigDecimal overallScore,
            BigDecimal pronunciationScore,
            BigDecimal intonationScore,
            BigDecimal speedWpm,
            SpeedStatus speedStatus,
            BigDecimal stressScore,
            BigDecimal pauseScore,
            String strengthsText,
            String weaknessesText,
            String summaryFeedback
    ) {
        this.status = AnalysisStatus.COMPLETED;
        this.transcript = transcript;
        this.sttConfidence = sttConfidence;
        this.sttModelName = sttModelName;
        this.overallScore = overallScore;
        this.pronunciationScore = pronunciationScore;
        this.intonationScore = intonationScore;
        this.speedWpm = speedWpm;
        this.speedStatus = speedStatus;
        this.stressScore = stressScore;
        this.pauseScore = pauseScore;
        this.strengthsText = strengthsText;
        this.weaknessesText = weaknessesText;
        this.summaryFeedback = summaryFeedback;
        this.failureReason = null;
        this.analyzedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
    }

    public void fail(String failureReason) {
        this.status = AnalysisStatus.FAILED;
        this.failureReason = failureReason;
        this.analyzedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
    }

    public OffsetDateTime updatedAt() {
        return analyzedAt == null ? createdAt : analyzedAt;
    }

    public boolean isCompleted() {
        return status == AnalysisStatus.COMPLETED;
    }

    public boolean canStartProcessing() {
        return status == AnalysisStatus.PENDING;
    }

    public boolean canFinish() {
        return status == AnalysisStatus.PENDING || status == AnalysisStatus.PROCESSING;
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
}
