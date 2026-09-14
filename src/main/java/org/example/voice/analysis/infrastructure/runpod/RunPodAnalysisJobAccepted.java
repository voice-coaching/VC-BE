package org.example.voice.analysis.infrastructure.runpod;

import java.util.UUID;

record RunPodAnalysisJobAccepted(
        UUID requestId,
        UUID executionId,
        String status
) {
}
