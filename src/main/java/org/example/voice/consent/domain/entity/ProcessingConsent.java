package org.example.voice.consent.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.consent.domain.type.ProcessingConsentScope;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "processing_consents")
public class ProcessingConsent {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern POLICY_REVISION = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = true)
    private Long userId;

    @Column(name = "training_session_id", nullable = true)
    private Long trainingSessionId;

    @Column(name = "recording_id")
    private Long recordingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = true, length = 40)
    private ProcessingConsentScope scope;

    @Column(name = "policy_revision", nullable = true, length = 100)
    private String policyRevision;

    @Column(name = "subject_sha256", nullable = true, length = 64)
    private String subjectSha256;

    @Column(name = "request_event_id", length = 36)
    private String requestEventId;

    @Column(name = "receipt_sha256", nullable = true, unique = false, length = 64)
    private String receiptSha256;

    @Column(name = "granted_at", nullable = true)
    private OffsetDateTime grantedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    private ProcessingConsent(
            Long userId,
            Long trainingSessionId,
            Long recordingId,
            ProcessingConsentScope scope,
            String policyRevision,
            String subjectSha256,
            UUID requestEventId,
            String receiptSha256,
            OffsetDateTime grantedAt
    ) {
        this.userId = userId;
        this.trainingSessionId = trainingSessionId;
        this.recordingId = recordingId;
        this.scope = scope;
        this.policyRevision = policyRevision;
        this.subjectSha256 = subjectSha256;
        this.requestEventId = requestEventId == null ? null : requestEventId.toString();
        this.receiptSha256 = receiptSha256;
        this.grantedAt = grantedAt;
    }

    public static ProcessingConsent grant(
            Long userId,
            Long trainingSessionId,
            Long recordingId,
            ProcessingConsentScope scope,
            String policyRevision,
            String subjectSha256,
            UUID requestEventId,
            String receiptSha256,
            OffsetDateTime grantedAt
    ) {

        boolean voiceBinding = scope == ProcessingConsentScope.VOICE_ANALYSIS
                && recordingId != null
                && recordingId > 0
                && requestEventId != null;
        boolean faceBinding = scope == ProcessingConsentScope.FACE_VIDEO_PROCESSING
                && recordingId == null
                && requestEventId == null;

        return new ProcessingConsent(
                userId,
                trainingSessionId,
                recordingId,
                scope,
                policyRevision,
                subjectSha256,
                requestEventId,
                receiptSha256,
                grantedAt
        );
    }
}
