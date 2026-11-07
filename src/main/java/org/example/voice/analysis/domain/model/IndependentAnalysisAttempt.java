package org.example.voice.analysis.domain.model;

import java.util.UUID;

/** Durable Backend/AI correlation; AI UUIDs never replace the Backend integer identifiers. */
public record IndependentAnalysisAttempt(UUID clientRequestId, UUID backendExecutionId,
        long analysisId, long recordingId, long ownerId, long sessionId, long contentId,
        String audioObjectKey, String audioSha256, long audioSize, String scriptCiphertext, String scriptSha256,
        org.example.voice.analysis.domain.type.IndependentAnalysisStage stage, UUID jobId, UUID aiExecutionId, String aiStatus, UUID claimId,
        int attempts, String resultSha256, byte[] resultBytes, boolean current, boolean cancelRequested) {
    @Override public String toString() { return "IndependentAnalysisAttempt[request="+clientRequestId+"]"; }
}
