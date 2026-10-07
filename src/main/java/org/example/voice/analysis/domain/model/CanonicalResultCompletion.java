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
        if(status!=AnalysisStatus.COMPLETED && status!=AnalysisStatus.FAILED)
            throw new IllegalArgumentException("CANONICAL_TERMINAL_STATUS_REQUIRED");
        if(payloadSha256==null || !payloadSha256.matches("[0-9a-f]{64}")
                || audioSha256==null || !audioSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("CANONICAL_DIGEST_REQUIRED");
        if((status==AnalysisStatus.FAILED && (failureCode==null || failureReason==null))
                || (status==AnalysisStatus.COMPLETED && (failureCode!=null || failureReason!=null)))
            throw new IllegalArgumentException("CANONICAL_FAILURE_REQUIRED");
        if(overallScore!=null && (status!=AnalysisStatus.COMPLETED || overallScore.signum()<0
                || overallScore.compareTo(java.math.BigDecimal.valueOf(100))>0
                || overallScore.stripTrailingZeros().scale()>1))
            throw new IllegalArgumentException("CANONICAL_SCORE_INVALID");
    }
}
