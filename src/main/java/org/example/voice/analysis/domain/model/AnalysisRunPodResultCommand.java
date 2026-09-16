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
        AnalysisWorkerResult workerResult
) {
}
