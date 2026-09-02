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
 * analysis request and the non-transactional Redis XADD side effect.
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

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @ManyToOne
    @JoinColumn(name = "analysis_id", nullable = false)
    private AnalysisResult analysisResult;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisRequestOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "request_stream_id", length = 64)
    private String requestStreamId;

    @Column(name = "retention_protocol_version")
    private Integer retentionProtocolVersion;

    private AnalysisRequestOutbox(UUID eventId, AnalysisResult analysisResult, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.eventId = eventId.toString();
        this.analysisResult = analysisResult;
        this.payload = payload;
        this.status = AnalysisRequestOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.retentionProtocolVersion = RETENTION_PROTOCOL_VERSION;
    }

    public static AnalysisRequestOutbox pending(UUID eventId, AnalysisResult analysisResult, String payload) {
        return new AnalysisRequestOutbox(eventId, analysisResult, payload);
    }

    public void markPublished(String streamId) {
        if (streamId == null || !streamId.matches("[0-9]+-[0-9]+") || streamId.length() > 64) {
            throw new IllegalArgumentException("analysis request stream ID is invalid");
        }
        this.status = AnalysisRequestOutboxStatus.PUBLISHED;
        this.requestStreamId = streamId;
        this.publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastErrorCode = null;
    }

    public boolean cancelPending(String errorCode) {
        if (status != AnalysisRequestOutboxStatus.PENDING) {
            return false;
        }
        this.status = AnalysisRequestOutboxStatus.FAILED;
        this.lastErrorCode = errorCode;
        return true;
    }

    /** @return true when this record reached its final delivery failure. */
    public boolean recordDispatchFailure(String errorCode, int maxAttempts) {
        this.attemptCount += 1;
        this.lastErrorCode = errorCode;
        if (attemptCount >= maxAttempts) {
            this.status = AnalysisRequestOutboxStatus.FAILED;
            return true;
        }
        this.nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(Math.min(300L, 30L * attemptCount));
        return false;
    }
}
