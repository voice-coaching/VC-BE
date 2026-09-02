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

    private AnalysisRequestOutbox(UUID eventId, AnalysisResult analysisResult, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.eventId = eventId.toString();
        this.analysisResult = analysisResult;
        this.payload = payload;
        this.status = AnalysisRequestOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public static AnalysisRequestOutbox pending(UUID eventId, AnalysisResult analysisResult, String payload) {
        return new AnalysisRequestOutbox(eventId, analysisResult, payload);
    }

    public void markPublished() {
        this.status = AnalysisRequestOutboxStatus.PUBLISHED;
        this.publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastErrorCode = null;
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
