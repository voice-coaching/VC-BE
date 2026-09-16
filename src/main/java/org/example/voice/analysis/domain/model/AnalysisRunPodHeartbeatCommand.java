package org.example.voice.analysis.domain.model;


import java.util.UUID;

public record AnalysisRunPodHeartbeatCommand(
        UUID requestId,
        UUID executionId,
        String workerInstanceId
) {
}
