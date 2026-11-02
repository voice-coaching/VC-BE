package org.example.voice.analysis.domain.model;

import org.example.voice.analysis.domain.type.AnalysisStatus;
import java.util.Objects;
import java.util.UUID;

/** Application-owned, verified completion intent. Not a wire DTO or a legacy worker result. */
public record CanonicalResultCompletion(UUID eventId, UUID requestId, UUID executionId,
                                        String payloadSha256, AnalysisStatus status,
                                        String audioSha256, String workerRevision, String pipelineRevision,
                                        String summary, String failureCode, String failureReason,
                                        java.math.BigDecimal overallScore) {
    public CanonicalResultCompletion(UUID eventId, UUID requestId, UUID executionId,
            String payloadSha256, AnalysisStatus status, String audioSha256,
            String workerRevision, String pipelineRevision, String summary,
            String failureCode, String failureReason) {
        this(eventId,requestId,executionId,payloadSha256,status,audioSha256,workerRevision,
                pipelineRevision,summary,failureCode,failureReason,null);
    }
    public CanonicalResultCompletion {
        Objects.requireNonNull(eventId);Objects.requireNonNull(requestId);Objects.requireNonNull(executionId);

    }
}
