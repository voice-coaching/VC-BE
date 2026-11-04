package org.example.voice.analysis.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Durable dispatch boundary between the PostgreSQL transaction that accepts an
 * analysis request and the non-transactional external delivery side effect.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "analysis_request_outbox")
public class AnalysisRequestOutbox {

    public static final int RETENTION_PROTOCOL_VERSION = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = true, unique = false, length = 36)
    private String eventId;

    @ManyToOne
    @JoinColumn(name = "analysis_id", nullable = true)
    private AnalysisResult analysisResult;

    @Column(name = "payload", nullable = true, columnDefinition = "text")
    private String payload;

    @Column(name = "private_payload_ciphertext", columnDefinition = "text")
    private org.example.voice.practicecontent.domain.model.PrivateText privatePayload;

    public String getPayload() { return privatePayload == null ? payload : privatePayload.value(); }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = true, length = 20)
    private AnalysisRequestOutboxStatus status;

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

    @Column(name = "request_stream_id", length = 64)
    private String requestStreamId;

    @Column(name = "transport", nullable = true, length = 20)
    private String transport;

    @Column(name = "execution_id", length = 36)
    private String executionId;

    @Column(name = "delivery_reference", length = 128)
    private String deliveryReference;

    @Column(name = "retention_protocol_version")
    private Integer retentionProtocolVersion;

    private AnalysisRequestOutbox(UUID eventId, AnalysisResult analysisResult, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.eventId = eventId.toString();
        this.analysisResult = analysisResult;
        if (analysisResult.getRecording() != null && analysisResult.getRecording().getTrainingSession() != null
                && analysisResult.getRecording().getTrainingSession().getContent() != null
                && analysisResult.getRecording().getTrainingSession().getContent().getOwnerId() != null) {
            this.payload = "{}";
            this.privatePayload = new org.example.voice.practicecontent.domain.model.PrivateText(payload);
        } else {
            this.payload = payload;
        }
        this.status = AnalysisRequestOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.retentionProtocolVersion = RETENTION_PROTOCOL_VERSION;
        this.transport = "REDIS_STREAM";
    }

    public static AnalysisRequestOutbox pending(UUID eventId, AnalysisResult analysisResult, String payload) {
        return new AnalysisRequestOutbox(eventId, analysisResult, payload);
    }

    public static AnalysisRequestOutbox pendingHttp(UUID eventId, UUID executionId, AnalysisResult analysisResult, String payload) {
        AnalysisRequestOutbox outbox = new AnalysisRequestOutbox(eventId, analysisResult, payload);
        outbox.transport = "RUNPOD_HTTP";
        outbox.executionId = executionId.toString();
        return outbox;
    }

    public void markPublished(String streamId) {

        this.status = AnalysisRequestOutboxStatus.PUBLISHED;
        this.requestStreamId = streamId;
        this.publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastErrorCode = null;
    }

    public void markDelivered(String reference) {
        this.status = AnalysisRequestOutboxStatus.PUBLISHED;
        this.deliveryReference = reference == null || reference.isBlank() ? eventId : reference;
        this.publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastErrorCode = null;
    }

    @Column(name = "busy_count", nullable = true)
    private int busyCount;

    /** Capacity is scheduling pressure, not a failed inference/delivery attempt. */
    public OffsetDateTime deferCapacity(int retryAfterSeconds) {
        busyCount = Math.min(Integer.MAX_VALUE - 1, busyCount) + 1;
        lastErrorCode = "runpod_capacity_busy";
        long delay = Math.max(retryAfterSeconds, Math.min(5, 1L << Math.min(busyCount - 1, 3)));
        nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delay)
                .plusNanos(java.util.concurrent.ThreadLocalRandom.current().nextLong(250_000_001));
        return nextAttemptAt;
    }

    /** Short database reservation; no lock or transaction is held across the worker HTTP call. */
    public void reserveDeliveryUntil(OffsetDateTime until) {
        this.nextAttemptAt = until;
    }

    public boolean cancelPending(String errorCode) {

        this.status = AnalysisRequestOutboxStatus.FAILED;
        this.lastErrorCode = errorCode;
        return true;
    }

    /** @return true when this record reached its final delivery failure. */
    public boolean recordDispatchFailure(String errorCode, int maxAttempts) {
        this.attemptCount+=1;
        this.lastErrorCode=errorCode;
        this.status=AnalysisRequestOutboxStatus.PENDING;
        this.nextAttemptAt=OffsetDateTime.now(ZoneOffset.UTC);
        return false;
    }

    /** HTTP delivery retries are bounded and honor the worker's Retry-After. */
    public boolean recordDispatchFailure(String errorCode, int maxAttempts, int retryAfterSeconds) {
        int previous = attemptCount == null ? 0 : attemptCount;
        this.attemptCount = Math.min(Integer.MAX_VALUE - 1, Math.max(0, previous)) + 1;
        this.lastErrorCode = errorCode;
        if (attemptCount >= Math.max(1, maxAttempts)) {
            this.status = AnalysisRequestOutboxStatus.FAILED;
            return true;
        }
        long backoffSeconds = Math.min(300L, 30L << Math.min(attemptCount - 1, 4));
        long delaySeconds = Math.max(backoffSeconds, Math.min(300, Math.max(1, retryAfterSeconds)));
        this.nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delaySeconds);
        return false;
    }
}
