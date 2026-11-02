package org.example.voice.analysis.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.domain.type.SpeedStatus;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Versioned AI-to-Backend result payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisWorkerResult(
        String schemaVersion,
        UUID eventId,
        UUID requestEventId,
        Long analysisId,
        AnalysisStatus status,
        AnalysisOutcome outcome,
        String failureCode,
        String failureReason,
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
        String summaryFeedback,
        AnalysisWorkerPronunciationEvidence pronunciationEvidence,
        String workerRevision,
        String pipelineRevision,
        String audioSha256,
        List<AnalysisWorkerSegment> segments,
        AnalysisWorkerVisualSupplement visualSupplement,
        Map<String, Object> seungunProductionEvidence,
        AnalysisClosedBetaDebug closedBetaDebug
) {
    public static final String SCHEMA_VERSION = "voice-coaching.analysis-result.v4";
    public static final String LEGACY_SCHEMA_VERSION = "voice-coaching.analysis-result.v3";

    public AnalysisWorkerResult(
            String schemaVersion,
            UUID eventId,
            UUID requestEventId,
            Long analysisId,
            AnalysisStatus status,
            AnalysisOutcome outcome,
            String failureCode,
            String failureReason,
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
            String summaryFeedback,
            AnalysisWorkerPronunciationEvidence pronunciationEvidence,
            String workerRevision,
            String pipelineRevision,
            String audioSha256,
            List<AnalysisWorkerSegment> segments
    ) {
        this(legacySchema(schemaVersion), eventId, requestEventId, analysisId, status, outcome,
                failureCode, failureReason, transcript, sttConfidence, sttModelName,
                overallScore, pronunciationScore, intonationScore, speedWpm,
                speedStatus, stressScore, pauseScore, strengthsText, weaknessesText,
                summaryFeedback, pronunciationEvidence, workerRevision,
                pipelineRevision, audioSha256, segments, null, null, null);
    }

    public AnalysisWorkerResult(
            String schemaVersion,
            UUID eventId,
            UUID requestEventId,
            Long analysisId,
            AnalysisStatus status,
            AnalysisOutcome outcome,
            String failureCode,
            String failureReason,
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
            String summaryFeedback,
            AnalysisWorkerPronunciationEvidence pronunciationEvidence,
            String workerRevision,
            String pipelineRevision,
            String audioSha256,
            List<AnalysisWorkerSegment> segments,
            AnalysisWorkerVisualSupplement visualSupplement
    ) {
        this(legacySchema(schemaVersion), eventId, requestEventId, analysisId, status, outcome,
                failureCode, failureReason, transcript, sttConfidence, sttModelName,
                overallScore, pronunciationScore, intonationScore, speedWpm,
                speedStatus, stressScore, pauseScore, strengthsText, weaknessesText,
                summaryFeedback, pronunciationEvidence, workerRevision,
                pipelineRevision, audioSha256, segments, visualSupplement, null, null);
    }

    public AnalysisWorkerResult {

        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(requestEventId, "requestEventId");

        Objects.requireNonNull(status, "status");
        segments = segments == null ? List.of() : List.copyOf(segments);
        seungunProductionEvidence = seungunProductionEvidence == null
                ? null
                : Map.copyOf(seungunProductionEvidence);

        if (status == AnalysisStatus.COMPLETED) {

            if (outcome == AnalysisOutcome.COACHING_READY) {

                Objects.requireNonNull(pronunciationEvidence,
                        "COACHING_READY result requires pronunciationEvidence");

            } else {}

        } else {}

        if (SCHEMA_VERSION.equals(schemaVersion)) {

            if (status == AnalysisStatus.COMPLETED) {

            } else {}
        } else {}

        Set<Integer> sequenceNumbers = new HashSet<>();
        for (AnalysisWorkerSegment segment : segments) {

        }
    }

    private static String legacySchema(String value) {

        return LEGACY_SCHEMA_VERSION;
    }

    private static boolean hasAnalysisPayload(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return true;
            }
        }
        return false;
    }
}
