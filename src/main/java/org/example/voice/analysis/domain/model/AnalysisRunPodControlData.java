package org.example.voice.analysis.domain.model;

import java.util.UUID;

public record AnalysisRunPodControlData(
        Long analysisId,
        UUID requestId,
        UUID executionId,
        boolean accepted,
        boolean canceled,
        boolean continueProcessing
) {
}
