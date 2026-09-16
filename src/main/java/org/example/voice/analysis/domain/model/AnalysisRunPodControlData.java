package org.example.voice.analysis.domain.model;

import java.util.UUID;

public record AnalysisRunPodControlData(
        Long analysisId,
        UUID requestId,
        UUID executionId,
        String workerInstanceId,
        String serverTime,
        String leaseExpiresAt,
        boolean continueProcessing
) {
}
