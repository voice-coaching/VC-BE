package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PRIVATE current-generation read, never a public response or a retained-history fallback.
 * Reads only committed, previously verified bytes; no B2/network/process work here.
 * Does not repeat inference or turn schema validity into a semantic-verification receipt.
 */
@Component
@RequiredArgsConstructor
public final class CanonicalCommittedResultReader {
    private final JdbcTemplate jdbc;
    private final RunPodContract contract;
    private record Stored(byte[] bytes, String rawSha, String payloadSha, UUID receiptId, String manifestSha) {}

    public Optional<CanonicalCallbackDocument> findCurrent(AnalysisResult result) {
        var bytes=jdbc.query("""
                SELECT callback_bytes FROM analysis_canonical_results
                WHERE analysis_id=? AND execution_id::text=? AND request_id::text=?
                ORDER BY applied_at DESC
                """,(row,index)->row.getBytes(1),result.getId(),result.getActiveExecutionId(),result.getActiveRequestEventId());
        return bytes.stream().map(raw -> CanonicalCallbackDocument.parse(raw,contract))
                .filter(doc -> CanonicalResultAttempt.visible(result,doc)).findFirst();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("CANONICAL_RESULT_UNAVAILABLE");
    }
}
