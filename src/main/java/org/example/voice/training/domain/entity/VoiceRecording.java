package org.example.voice.training.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.training.domain.type.RecordingQualityStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "voice_recordings")
public class VoiceRecording {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_session_id", nullable = false)
    private TrainingSession trainingSession;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "audio_url", nullable = false)
    private String audioUrl;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "audio_sha256", length = 64)
    private String audioSha256;

    @Column(name = "visual_object_key", length = 1000)
    private String visualObjectKey;

    @Column(name = "visual_mime_type", length = 100)
    private String visualMimeType;

    @Column(name = "visual_file_size_bytes")
    private Long visualFileSizeBytes;

    @Column(name = "visual_sha256", length = 64)
    private String visualSha256;

    @Column(name = "visual_consent_receipt_sha256", length = 64)
    private String visualConsentReceiptSha256;

    @Column(name = "visual_consent_policy_revision", length = 100)
    private String visualConsentPolicyRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false)
    private RecordingQualityStatus qualityStatus;

    @Column(name = "volume_score")
    private BigDecimal volumeScore;

    @Column(name = "noise_score")
    private BigDecimal noiseScore;

    @Column(name = "is_selected", nullable = false)
    private Boolean selected;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private VoiceRecording(
            TrainingSession trainingSession,
            Integer attemptNo,
            String audioUrl,
            String mimeType,
            Long fileSizeBytes,
            Integer durationMs,
            String audioSha256,
            String visualObjectKey,
            String visualMimeType,
            Long visualFileSizeBytes,
            String visualSha256,
            String visualConsentReceiptSha256,
            String visualConsentPolicyRevision,
            RecordingQualityStatus qualityStatus,
            BigDecimal volumeScore,
            BigDecimal noiseScore
    ) {
        this.trainingSession = trainingSession;
        this.attemptNo = attemptNo;
        this.audioUrl = audioUrl;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.durationMs = durationMs;
        this.audioSha256 = audioSha256;
        this.visualObjectKey = visualObjectKey;
        this.visualMimeType = visualMimeType;
        this.visualFileSizeBytes = visualFileSizeBytes;
        this.visualSha256 = visualSha256;
        this.visualConsentReceiptSha256 = visualConsentReceiptSha256;
        this.visualConsentPolicyRevision = visualConsentPolicyRevision;
        this.qualityStatus = qualityStatus;
        this.volumeScore = volumeScore;
        this.noiseScore = noiseScore;
        this.selected = false;
        this.createdAt = OffsetDateTime.now(SEOUL_ZONE_ID);
    }

    public static VoiceRecording create(
            TrainingSession trainingSession,
            Integer attemptNo,
            String audioUrl,
            String mimeType,
            Long fileSizeBytes,
            Integer durationMs,
            String audioSha256,
            RecordingQualityStatus qualityStatus,
            BigDecimal volumeScore,
            BigDecimal noiseScore
    ) {
        return create(trainingSession, attemptNo, audioUrl, mimeType, fileSizeBytes,
                durationMs, audioSha256, null, null, null, null, null, null,
                qualityStatus, volumeScore, noiseScore);
    }

    public static VoiceRecording create(
            TrainingSession trainingSession,
            Integer attemptNo,
            String audioUrl,
            String mimeType,
            Long fileSizeBytes,
            Integer durationMs,
            String audioSha256,
            String visualObjectKey,
            String visualMimeType,
            Long visualFileSizeBytes,
            String visualSha256,
            String visualConsentReceiptSha256,
            String visualConsentPolicyRevision,
            RecordingQualityStatus qualityStatus,
            BigDecimal volumeScore,
            BigDecimal noiseScore
    ) {
        return new VoiceRecording(
                trainingSession,
                attemptNo,
                audioUrl,
                mimeType,
                fileSizeBytes,
                durationMs,
                audioSha256,
                visualObjectKey,
                visualMimeType,
                visualFileSizeBytes,
                visualSha256,
                visualConsentReceiptSha256,
                visualConsentPolicyRevision,
                qualityStatus,
                volumeScore,
                noiseScore
        );
    }

    public void select() {
        this.selected = true;
    }

    public void unselect() {
        this.selected = false;
    }

    public void delete() {
        this.deletedAt = OffsetDateTime.now(SEOUL_ZONE_ID);
        this.selected = false;
    }
}
