package org.example.voice.analysis.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnalysisRunPodClaimCommand(
        String schemaVersion,
        UUID requestId,
        UUID executionId,
        String workerInstanceId,
        OffsetDateTime claimedUntil
) {
}
