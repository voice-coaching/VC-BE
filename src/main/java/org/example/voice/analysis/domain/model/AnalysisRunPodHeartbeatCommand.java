package org.example.voice.analysis.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnalysisRunPodHeartbeatCommand(
        String schemaVersion,
        UUID requestId,
        UUID executionId,
        String workerInstanceId,
        OffsetDateTime heartbeatAt
) {
}
