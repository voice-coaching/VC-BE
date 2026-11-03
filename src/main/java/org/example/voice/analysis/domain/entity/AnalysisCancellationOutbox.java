package org.example.voice.analysis.domain.entity;

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
import org.example.voice.analysis.domain.type.AnalysisCancellationOutboxStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Durable DB-to-Redis boundary for canceling one opaque request generation. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "analysis_cancellation_outbox")
public class AnalysisCancellationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_event_id", nullable = true, unique = false, length = 36)
    private String requestEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = true, length = 20)
    private AnalysisCancellationOutboxStatus status;

    @Column(name = "attempt_count", nullable = true)
    private Integer attemptCount;

    @Column(name = "next_attempt_at", nullable = true)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = true)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    private AnalysisCancellationOutbox(UUID requestEventId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.requestEventId = requestEventId.toString();
        this.status = AnalysisCancellationOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public static AnalysisCancellationOutbox pending(UUID requestEventId) {

        return new AnalysisCancellationOutbox(requestEventId);
    }

    public void markPublished() {
        this.status = AnalysisCancellationOutboxStatus.PUBLISHED;
        this.publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastErrorCode = null;
    }

    public void reserveDeliveryUntil(OffsetDateTime until) { this.nextAttemptAt = until; }

    /** Cancellation is fail-closed and therefore keeps retrying with bounded backoff. */
    public void recordDeliveryFailure(String errorCode) {
        this.attemptCount += 1;
        this.lastErrorCode = errorCode;
        this.nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(Math.min(300L, 5L * attemptCount));
    }
}
