package org.example.voice.analysis.domain.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.example.voice.analysis.domain.model.IndependentAnalysisAttempt;

public interface IndependentAnalysisClient {
    record Job(UUID clientRequestId, UUID jobId, UUID executionId, String status, long sequence,
               boolean resultAvailable, String resultSha256, String failureCode, OffsetDateTime acknowledgedAt) {
        public boolean terminal() { return java.util.Set.of("COMPLETED","FAILED","CANCELLED").contains(status); }
    }
    record Result(byte[] bytes, String sha256) {
        @Override public String toString() { return "IndependentResult[sha256="+sha256+"]"; }
    }
    Optional<Job> find(UUID clientRequestId);
    Job submit(IndependentAnalysisAttempt attempt, byte[] wav, String script);
    Job status(UUID jobId);
    Result result(UUID jobId);
    Job acknowledge(UUID jobId, String sha256);
    Job cancel(UUID jobId);
}
