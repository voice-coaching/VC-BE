package org.example.voice.analysis.domain.model;

import java.util.UUID;

/** Routing identity from the current, owned execution, not a default schema. */
public record CanonicalResultContract(long analysisId, long recordingId, UUID requestId,
                                      UUID executionId, String analysisProfile, String resultSchemaVersion) {}
