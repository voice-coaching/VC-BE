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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.Map;

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

    @Column(name = "failure_reason", length = 500)
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

    @Column(name = "pronunciation_evidence_schema_version", length = 100)
    private String pronunciationEvidenceSchemaVersion;

    @Column(name = "selected_phone", length = 16)
    private String selectedPhone;

    @Column(name = "selected_expected_index")
    private Integer selectedExpectedIndex;

    @Column(name = "selected_start_ms")
    private Integer selectedStartMs;

    @Column(name = "selected_end_ms")
    private Integer selectedEndMs;

    @Column(name = "detector_score", precision = 8, scale = 6)
    private BigDecimal detectorScore;

    @Column(name = "operating_threshold", precision = 8, scale = 6)
    private BigDecimal operatingThreshold;

    @Column(name = "score_semantics", length = 100)
    private String scoreSemantics;

    @Column(name = "evidence_state", length = 100)
    private String evidenceState;

    @Column(name = "visual_supplement_schema_version", length = 100)
    private String visualSupplementSchemaVersion;

    @Column(name = "visual_evidence_relation", length = 40)
    private String visualEvidenceRelation;

    @Column(name = "visual_approved_claim_id", length = 192)
    private String visualApprovedClaimId;

    @Column(name = "visual_renderer_key", length = 192)
    private String visualRendererKey;

    @Column(name = "visual_phone_anchor_ref", length = 64)
    private String visualPhoneAnchorRef;

    @Column(name = "visual_supplement_sha256", length = 64)
    private String visualSupplementSha256;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visual_closed_beta_lip_observation", columnDefinition = "jsonb")
    private Map<String, Object> visualClosedBetaLipObservation;

    @Column(name = "feedback_regeneration_count", nullable = false)
    private Integer feedbackRegenerationCount;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "feedback_regenerated_at")
    private OffsetDateTime feedbackRegeneratedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private AnalysisResult(VoiceRecording recording, UUID requestEventId) {
        this.recording = recording;
        this.status = AnalysisStatus.PENDING;
        this.activeRequestEventId = requestEventId.toString();
        this.feedbackRegenerationCount = 0;
        this.retryCount = 0;
        this.createdAt = OffsetDateTime.now(SEOUL_ZONE_ID);
        this.analyzedAt = this.createdAt;
    }

    public static AnalysisResult pending(VoiceRecording recording, UUID requestEventId) {
        return new AnalysisResult(recording, requestEventId);
    }

    public void retry(UUID requestEventId) {
        this.status = AnalysisStatus.PENDING;
        this.activeRequestEventId = requestEventId.toString();
        this.retryCount += 1;
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
        if (result.pronunciationEvidence() != null) {
            this.pronunciationEvidenceSchemaVersion = result.pronunciationEvidence().schemaVersion();
            this.selectedPhone = result.pronunciationEvidence().selectedPhone();
            this.selectedExpectedIndex = result.pronunciationEvidence().selectedExpectedIndex();
            this.selectedStartMs = result.pronunciationEvidence().selectedStartMs();
            this.selectedEndMs = result.pronunciationEvidence().selectedEndMs();
            this.detectorScore = result.pronunciationEvidence().detectorScore();
            this.operatingThreshold = result.pronunciationEvidence().operatingThreshold();
            this.scoreSemantics = result.pronunciationEvidence().scoreSemantics();
            this.evidenceState = result.pronunciationEvidence().evidenceState();
        }
        if (result.visualSupplement() != null) {
            this.visualSupplementSchemaVersion = result.visualSupplement().schemaVersion();
            this.visualEvidenceRelation = result.visualSupplement().evidenceRelation();
            this.visualApprovedClaimId = result.visualSupplement().approvedClaimId();
            this.visualRendererKey = result.visualSupplement().rendererKey();
            this.visualPhoneAnchorRef = result.visualSupplement().upstreamPhoneAnchorRef();
            this.visualSupplementSha256 = result.visualSupplement().supplementSha256();
            this.visualClosedBetaLipObservation = (
                    result.visualSupplement().closedBetaLipObservation() == null
                            ? null
                            : Map.copyOf(result.visualSupplement().closedBetaLipObservation())
            );
        }
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

    /**
     * Discards every result for a session the user canceled. A completed AI
     * result is still non-authoritative until the training session itself is
     * completed, so cancellation deliberately wins a concurrent late result.
     */
    public boolean cancel(String code, String reason) {
        if (status == AnalysisStatus.FAILED && code.equals(failureCode)) {
            return false;
        }
        clearWorkerResult();
        this.status = AnalysisStatus.FAILED;
        this.failureCode = code;
        this.failureReason = reason;
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
        this.pronunciationEvidenceSchemaVersion = null;
        this.selectedPhone = null;
        this.selectedExpectedIndex = null;
        this.selectedStartMs = null;
        this.selectedEndMs = null;
        this.detectorScore = null;
        this.operatingThreshold = null;
        this.scoreSemantics = null;
        this.evidenceState = null;
        this.visualSupplementSchemaVersion = null;
        this.visualEvidenceRelation = null;
        this.visualApprovedClaimId = null;
        this.visualRendererKey = null;
        this.visualPhoneAnchorRef = null;
        this.visualSupplementSha256 = null;
        this.visualClosedBetaLipObservation = null;
    }
}
