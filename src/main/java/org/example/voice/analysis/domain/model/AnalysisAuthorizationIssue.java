package org.example.voice.analysis.domain.model;

import org.example.voice.practicecontent.domain.type.LearningFocus;

import java.util.UUID;

/** Inputs bound into a per-request authorization grant. */
public record AnalysisAuthorizationIssue(
        UUID requestEventId,
        Long analysisId,
        Long contentId,
        String promptRevision,
        String scriptSha256,
        String audioObjectKey,
        String audioSha256,
        String mimeType,
        Long fileSizeBytes,
        Integer durationMs,
        LearningFocus learningFocus,
        String consentReceiptSha256,
        String consentPolicyRevision,
        AnalysisClosedBetaContext closedBetaContext,
        AnalysisWorkerVisualInput visualInput
) {
    public AnalysisAuthorizationIssue(
            UUID requestEventId,
            Long analysisId,
            Long contentId,
            String promptRevision,
            String scriptSha256,
            String audioObjectKey,
            String audioSha256,
            String mimeType,
            Long fileSizeBytes,
            Integer durationMs,
            LearningFocus learningFocus,
            String consentReceiptSha256,
            String consentPolicyRevision
    ) {
        this(requestEventId, analysisId, contentId, promptRevision, scriptSha256,
                audioObjectKey, audioSha256, mimeType, fileSizeBytes, durationMs,
                learningFocus, consentReceiptSha256, consentPolicyRevision, null,
                null);
    }
}
