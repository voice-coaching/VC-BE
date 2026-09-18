package org.example.voice.analysis.domain.model;

import org.example.voice.analysis.domain.type.AnalysisStatus;

import java.util.UUID;

public record AnalysisRunPodResultCommand(
        String schemaVersion,
        UUID eventId,
        UUID requestId,
        UUID executionId,
        String workerInstanceId,
        String payloadSha256,
        Long analysisId,
        Long recordingId,
        AnalysisStatus status,
        AnalysisWorkerResult workerResult,
        java.math.BigDecimal overallScore,
        ClovaScoreEvidence scoringEvidence,
        AnalysisCoaching coaching
) {
    public AnalysisRunPodResultCommand(String schemaVersion, UUID eventId, UUID requestId,
            UUID executionId, String workerInstanceId, String payloadSha256, Long analysisId,
            Long recordingId, AnalysisStatus status, AnalysisWorkerResult workerResult,
            java.math.BigDecimal overallScore, ClovaScoreEvidence scoringEvidence) {
        this(schemaVersion, eventId, requestId, executionId, workerInstanceId, payloadSha256,
                analysisId, recordingId, status, workerResult, overallScore, scoringEvidence, null);
    }
}
