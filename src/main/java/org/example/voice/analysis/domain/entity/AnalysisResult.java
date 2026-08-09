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

    @Column(name = "speed_status")
    private String speedStatus;

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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private AnalysisResult(VoiceRecording recording) {
        this.recording = recording;
        this.status = AnalysisStatus.PENDING;
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

    public OffsetDateTime updatedAt() {
        return analyzedAt == null ? createdAt : analyzedAt;
    }
}
