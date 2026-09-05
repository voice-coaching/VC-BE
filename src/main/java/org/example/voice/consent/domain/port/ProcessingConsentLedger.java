package org.example.voice.consent.domain.port;

import org.example.voice.consent.domain.model.ProcessingConsentReceipt;

import java.util.UUID;

public interface ProcessingConsentLedger {

    ProcessingConsentReceipt grantVoiceAnalysis(
            Long userId,
            Long sessionId,
            Long recordingId,
            UUID requestEventId,
            String policyRevision,
            String audioSha256
    );

    ProcessingConsentReceipt grantFaceVideoProcessing(
            Long userId,
            Long sessionId,
            String policyRevision,
            String sourceObjectKey
    );

    void revokeForSession(Long userId, Long sessionId);

    void revokeForUser(Long userId);
}
