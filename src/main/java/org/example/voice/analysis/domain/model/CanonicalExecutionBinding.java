package org.example.voice.analysis.domain.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Immutable request identity, not a canonical result, receipt, or admission approval. */
public record CanonicalExecutionBinding(
        UUID executionId,
        UUID requestId,
        Long analysisId,
        Long recordingId,
        Long contentId,
        String analysisProfile,
        String requestSchemaVersion,
        String resultSchemaVersion,
        String requestPayloadSha256,
        String scriptSha256,
        String audioSha256,
        OffsetDateTime deadlineAt
) {
    public CanonicalExecutionBinding {
        deadlineAt = deadlineAt.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
