package org.example.voice.training.domain.entity;

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
import org.example.voice.training.domain.type.RecordingUploadIntentStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "recording_upload_intents")
public class RecordingUploadIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "training_session_id", nullable = false)
    private Long trainingSessionId;

    @Column(name = "object_key", nullable = false, unique = true, length = 1000)
    private String objectKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordingUploadIntentStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    private RecordingUploadIntent(
            Long userId,
            Long trainingSessionId,
            String objectKey,
            String mimeType,
            Long fileSizeBytes,
            OffsetDateTime expiresAt
    ) {
        this.userId = userId;
        this.trainingSessionId = trainingSessionId;
        this.objectKey = objectKey;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.expiresAt = expiresAt;
        this.status = RecordingUploadIntentStatus.ISSUED;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public static RecordingUploadIntent issue(
            Long userId,
            Long trainingSessionId,
            String objectKey,
            String mimeType,
            Long fileSizeBytes,
            OffsetDateTime expiresAt
    ) {
        return new RecordingUploadIntent(
                userId,
                trainingSessionId,
                objectKey,
                mimeType,
                fileSizeBytes,
                expiresAt
        );
    }

    public void consume() {
        if (status == RecordingUploadIntentStatus.ISSUED) {
            status = RecordingUploadIntentStatus.CONSUMED;
            resolvedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public boolean expire() {
        if (status != RecordingUploadIntentStatus.ISSUED) {
            return false;
        }
        status = RecordingUploadIntentStatus.EXPIRED;
        resolvedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return true;
    }
}
