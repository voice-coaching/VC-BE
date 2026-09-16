package org.example.voice.analysis.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.voice.analysis.domain.model.AnalysisRunPodResultCommand;
import org.example.voice.analysis.domain.model.AnalysisWorkerPronunciationEvidence;
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;

import org.example.voice.analysis.domain.model.AnalysisWorkerVisualSupplement;
import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisStatus;



import java.util.List;
import java.util.UUID;

public record RunPodAnalysisResultCallbackRequestDto(
        @NotBlank String schemaVersion,
        @NotNull UUID eventId,
        @NotNull UUID requestId,
        @NotNull UUID executionId,
        @NotNull UUID workerInstanceId,
        @NotNull Long analysisId,
        @NotNull Long recordingId,
        @NotNull AnalysisStatus status,
        AnalysisOutcome outcome,
        String failureCode,
        String failureReason,
        String summaryFeedback,
        AnalysisWorkerPronunciationEvidence pronunciationEvidence,
        String workerRevision,
        String pipelineRevision,
        String audioSha256,
        AnalysisWorkerVisualSupplement visualSupplement
) {
    public AnalysisRunPodResultCommand toCommand(String payloadSha256) {
        return new AnalysisRunPodResultCommand(
                schemaVersion,
                eventId,
                requestId,
                executionId,
                workerInstanceId.toString(),
                payloadSha256,
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
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                summaryFeedback,
                pronunciationEvidence,
                workerRevision,
                pipelineRevision,
                audioSha256,
                List.of(),
                visualSupplement
        );
    }
}
