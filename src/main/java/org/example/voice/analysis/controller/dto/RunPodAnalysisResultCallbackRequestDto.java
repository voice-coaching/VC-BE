package org.example.voice.analysis.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.voice.analysis.domain.model.AnalysisRunPodResultCommand;
import org.example.voice.analysis.domain.model.AnalysisWorkerPronunciationEvidence;
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerSegment;
import org.example.voice.analysis.domain.model.AnalysisWorkerVisualSupplement;
import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.domain.type.SpeedStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RunPodAnalysisResultCallbackRequestDto(
        @NotBlank String schemaVersion,
        @NotNull UUID eventId,
        @NotNull UUID requestId,
        @NotNull UUID executionId,
        @NotNull Long analysisId,
        @NotNull Long recordingId,
        @NotNull AnalysisStatus status,
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
    public AnalysisRunPodResultCommand toCommand() {
        return new AnalysisRunPodResultCommand(
                schemaVersion,
                eventId,
                requestId,
                executionId,
                analysisId,
                recordingId,
                status,
                toWorkerResult()
        );
    }

    public AnalysisWorkerResult toWorkerResult() {
        return new AnalysisWorkerResult(
                AnalysisWorkerResult.LEGACY_SCHEMA_VERSION,
                eventId,
                requestId,
                analysisId,
                status,
                outcome,
                failureCode,
                failureReason,
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
                pronunciationEvidence,
                workerRevision,
                pipelineRevision,
                audioSha256,
                segments,
                visualSupplement
        );
    }
}
