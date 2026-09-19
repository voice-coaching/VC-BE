package org.example.voice.analysis.domain.model;


import java.util.UUID;

public record AnalysisRunPodClaimCommand(
        UUID requestId,
        UUID executionId,
        String workerInstanceId,
        String requestPayloadSha256
) {
}
