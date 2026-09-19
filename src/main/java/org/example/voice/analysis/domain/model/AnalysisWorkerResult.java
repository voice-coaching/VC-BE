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
@JsonIgnoreProperties(ignoreUnknown = false)
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
        if (!SCHEMA_VERSION.equals(schemaVersion) && !LEGACY_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion is unsupported");
        }
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(requestEventId, "requestEventId");
        requirePositive(analysisId, "analysisId");
        Objects.requireNonNull(status, "status");
        segments = segments == null ? List.of() : List.copyOf(segments);
        seungunProductionEvidence = seungunProductionEvidence == null
                ? null
                : Map.copyOf(seungunProductionEvidence);
        validateTerminalState(status, outcome, failureCode, failureReason, segments);
        requireLength(transcript, 20_000, "transcript");
        requireLength(sttModelName, 100, "sttModelName");
        requireLength(failureCode, 100, "failureCode");
        requireLength(failureReason, 500, "failureReason");
        requireLength(strengthsText, 20_000, "strengthsText");
        requireLength(weaknessesText, 20_000, "weaknessesText");
        requireLength(summaryFeedback, 20_000, "summaryFeedback");
        requireLength(workerRevision, 100, "workerRevision");
        requireLength(pipelineRevision, 100, "pipelineRevision");
        requireSha256(audioSha256, "audioSha256");
        requireScore(overallScore, "overallScore");
        requireScore(pronunciationScore, "pronunciationScore");
        requireScore(intonationScore, "intonationScore");
        requireScore(stressScore, "stressScore");
        requireScore(pauseScore, "pauseScore");
        if (status != AnalysisStatus.COMPLETED && hasAnalysisPayload(
                transcript,
                sttConfidence,
                sttModelName,
                overallScore,
                pronunciationScore,
                intonationScore,
                speedWpm,
                speedStatus,
                stressScore,
                pauseScore,
                strengthsText,
                weaknessesText,
                summaryFeedback,
                audioSha256
        )) {
            throw new IllegalArgumentException(status + " result must not contain analysis payload");
        }
        if (status == AnalysisStatus.COMPLETED) {
            if (outcome != AnalysisOutcome.COACHING_READY
                    && outcome != AnalysisOutcome.COMPLETED_NO_ISSUE) {
                throw new IllegalArgumentException("result outcome is unsupported");
            }
            requireText(workerRevision, 100, "workerRevision");
            requireText(pipelineRevision, 100, "pipelineRevision");
            if (audioSha256 == null) {
                throw new IllegalArgumentException("COMPLETED result requires audioSha256");
            }
            if (outcome == AnalysisOutcome.COACHING_READY) {
                requireText(summaryFeedback, 20_000, "summaryFeedback");
                Objects.requireNonNull(pronunciationEvidence,
                        "COACHING_READY result requires pronunciationEvidence");
                if (visualSupplement != null
                        && !visualSupplement.selectedExpectedIndex().equals(
                                pronunciationEvidence.selectedExpectedIndex())) {
                    throw new IllegalArgumentException(
                            "visual supplement must bind Seungun selected phone"
                    );
                }
            } else if (summaryFeedback != null || pronunciationEvidence != null) {
                throw new IllegalArgumentException(
                        "non-coaching outcome must not contain pronunciation coaching evidence"
                );
            }
            if (hasAnalysisPayload(
                    transcript,
                    sttConfidence,
                    sttModelName,
                    overallScore,
                    pronunciationScore,
                    intonationScore,
                    speedWpm,
                    speedStatus,
                    stressScore,
                    pauseScore,
                    strengthsText,
                    weaknessesText
            ) || !segments.isEmpty()) {
                throw new IllegalArgumentException(
                        "result does not accept unapproved transcript, score, or segment mappings"
                );
            }
        } else if (pronunciationEvidence != null || visualSupplement != null) {
            throw new IllegalArgumentException(status + " result must not contain coaching evidence");
        }
        if (status == AnalysisStatus.COMPLETED
                && outcome != AnalysisOutcome.COACHING_READY
                && visualSupplement != null) {
            throw new IllegalArgumentException("visual supplement requires coaching-ready Seungun evidence");
        }
        if (SCHEMA_VERSION.equals(schemaVersion)) {
            if (closedBetaDebug == null) {
                throw new IllegalArgumentException("result v4 requires closed beta debug payload");
            }
            if (status == AnalysisStatus.COMPLETED) {
                if (seungunProductionEvidence == null
                        || !"korean_phone_ctc.production_analysis.v2".equals(
                        seungunProductionEvidence.get("schema_version"))) {
                    throw new IllegalArgumentException(
                            "completed result v4 requires Seungun production evidence"
                    );
                }
            } else if (seungunProductionEvidence != null) {
                throw new IllegalArgumentException(
                        "non-completed result must not contain Seungun production evidence"
                );
            }
        } else if (closedBetaDebug != null || seungunProductionEvidence != null) {
            throw new IllegalArgumentException("result v3 must not contain v4 payload");
        }
        if (sttConfidence != null && (sttConfidence.signum() < 0 || sttConfidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("sttConfidence must be between 0 and 1");
        }
        if (speedWpm != null && speedWpm.signum() < 0) {
            throw new IllegalArgumentException("speedWpm must not be negative");
        }
        Set<Integer> sequenceNumbers = new HashSet<>();
        for (AnalysisWorkerSegment segment : segments) {
            if (!sequenceNumbers.add(segment.sequenceNo())) {
                throw new IllegalArgumentException("segments contain duplicate sequenceNo");
            }
        }
    }

    private static void validateTerminalState(
            AnalysisStatus status,
            AnalysisOutcome outcome,
            String failureCode,
            String failureReason,
            List<AnalysisWorkerSegment> segments
    ) {
        switch (status) {
            case PROCESSING -> {
                if (outcome != null || failureCode != null || failureReason != null || !segments.isEmpty()) {
                    throw new IllegalArgumentException("PROCESSING result must not contain terminal payload");
                }
            }
            case COMPLETED -> {
                if (outcome == null || failureCode != null || failureReason != null) {
                    throw new IllegalArgumentException("COMPLETED result must contain only an outcome");
                }
            }
            case FAILED -> {
                if (outcome != null || failureCode == null || failureReason == null || !segments.isEmpty()) {
                    throw new IllegalArgumentException("FAILED result must contain a failure code and reason only");
                }
            }
            case PENDING -> throw new IllegalArgumentException("PENDING is not a worker result status");
        }
    }

    private static void requireEquals(String expected, String value, String field) {
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(field + " is unsupported");
        }
    }

    private static String legacySchema(String value) {
        if (!SCHEMA_VERSION.equals(value) && !LEGACY_SCHEMA_VERSION.equals(value)) {
            throw new IllegalArgumentException("schemaVersion is unsupported");
        }
        return LEGACY_SCHEMA_VERSION;
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireLength(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void requireSha256(String value, String field) {
        if (value != null && !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lower-case SHA-256 digest");
        }
    }

    private static void requireScore(BigDecimal value, String field) {
        if (value != null && (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
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
