package org.example.voice.consent.infrastructure;

import org.example.voice.consent.domain.entity.ProcessingConsent;
import org.example.voice.consent.domain.model.ProcessingConsentReceipt;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.consent.domain.type.ProcessingConsentScope;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Repository
public class JpaProcessingConsentLedger implements ProcessingConsentLedger {

    private final ProcessingConsentJpaRepository repository;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public JpaProcessingConsentLedger(ProcessingConsentJpaRepository repository) {
        this(repository, Clock.systemUTC(), new SecureRandom());
    }

    JpaProcessingConsentLedger(
            ProcessingConsentJpaRepository repository,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.repository = repository;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Override
    @Transactional
    public ProcessingConsentReceipt grantVoiceAnalysis(
            Long userId,
            Long sessionId,
            Long recordingId,
            UUID requestEventId,
            String policyRevision,
            String audioSha256
    ) {
        return grant(
                userId,
                sessionId,
                recordingId,
                ProcessingConsentScope.VOICE_ANALYSIS,
                policyRevision,
                audioSha256,
                requestEventId
        );
    }

    @Override
    @Transactional
    public ProcessingConsentReceipt grantFaceVideoProcessing(
            Long userId,
            Long sessionId,
            String policyRevision,
            String sourceObjectKey
    ) {
        return grant(
                userId,
                sessionId,
                null,
                ProcessingConsentScope.FACE_VIDEO_PROCESSING,
                policyRevision,
                sha256(sourceObjectKey),
                null
        );
    }

    @Override
    @Transactional
    public void revokeForSession(Long userId, Long sessionId) {
        repository.revokeForSession(userId, sessionId, OffsetDateTime.now(clock));
    }

    @Override
    @Transactional
    public void revokeForUser(Long userId) {
        repository.revokeForUser(userId, OffsetDateTime.now(clock));
    }

    private ProcessingConsentReceipt grant(
            Long userId,
            Long sessionId,
            Long recordingId,
            ProcessingConsentScope scope,
            String policyRevision,
            String subjectSha256,
            UUID requestEventId
    ) {
        OffsetDateTime grantedAt = OffsetDateTime.now(clock);
        byte[] entropy = new byte[32];
        secureRandom.nextBytes(entropy);
        String receiptSha256 = sha256(
                userId + ":" + sessionId + ":" + scope + ":" + policyRevision + ":"
                        + subjectSha256 + ":" + grantedAt + ":" + HexFormat.of().formatHex(entropy)
        );
        ProcessingConsent consent = ProcessingConsent.grant(
                userId,
                sessionId,
                recordingId,
                scope,
                policyRevision,
                subjectSha256,
                requestEventId,
                receiptSha256,
                grantedAt
        );
        repository.saveAndFlush(consent);
        return new ProcessingConsentReceipt(receiptSha256, grantedAt);
    }

    private static String sha256(String value) {
        if (value == null || value.isBlank() || value.length() > 2_048) {
            throw new IllegalArgumentException("processing consent subject is invalid");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
