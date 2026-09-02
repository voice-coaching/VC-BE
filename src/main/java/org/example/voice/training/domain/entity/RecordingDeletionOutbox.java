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
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.example.voice.training.domain.type.RecordingDeletionStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "recording_deletion_outbox")
public class RecordingDeletionOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "training_session_id", nullable = false)
    private Long trainingSessionId;

    @Column(name = "object_key", nullable = false, unique = true, length = 1000)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private RecordingDeletionReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordingDeletionStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private RecordingDeletionOutbox(
            Long userId,
            Long trainingSessionId,
            String objectKey,
            RecordingDeletionReason reason
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.userId = userId;
        this.trainingSessionId = trainingSessionId;
        this.objectKey = objectKey;
        this.reason = reason;
        this.status = RecordingDeletionStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public static RecordingDeletionOutbox pending(
            Long userId,
            Long trainingSessionId,
            String objectKey,
            RecordingDeletionReason reason
    ) {
        return new RecordingDeletionOutbox(userId, trainingSessionId, objectKey, reason);
    }

    public void markDeleted() {
        status = RecordingDeletionStatus.DELETED;
        deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
        lastErrorCode = null;
    }

    public void recordFailure(String errorCode, int maxAttempts) {
        attemptCount += 1;
        lastErrorCode = errorCode;
        if (attemptCount >= maxAttempts) {
            status = RecordingDeletionStatus.FAILED;
            return;
        }
        nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(Math.min(3600L, 30L * (1L << Math.min(attemptCount - 1, 6))));
    }
}
