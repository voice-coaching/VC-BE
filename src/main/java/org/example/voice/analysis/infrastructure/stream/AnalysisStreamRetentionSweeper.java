package org.example.voice.analysis.infrastructure.stream;

import lombok.extern.slf4j.Slf4j;
import org.example.voice.analysis.domain.entity.AnalysisCancellationOutbox;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.type.AnalysisCancellationOutboxStatus;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.AnalysisCancellationOutboxJpaRepository;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Deletes transport metadata only after DB terminal state and Stream entry removal agree. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisStreamRetentionSweeper {

    private static final List<AnalysisRequestOutboxStatus> OUTBOX_TERMINAL = List.of(
            AnalysisRequestOutboxStatus.PUBLISHED,
            AnalysisRequestOutboxStatus.FAILED
    );
    private static final List<AnalysisStatus> ANALYSIS_TERMINAL = List.of(
            AnalysisStatus.COMPLETED,
            AnalysisStatus.FAILED
    );

    private final AnalysisRequestOutboxJpaRepository requestRepository;
    private final AnalysisCancellationOutboxJpaRepository cancellationRepository;
    private final RedisAnalysisRetentionStore redis;
    private final AnalysisStreamProperties properties;
    private final AnalysisStreamMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    public AnalysisStreamRetentionSweeper(
            AnalysisRequestOutboxJpaRepository requestRepository,
            AnalysisCancellationOutboxJpaRepository cancellationRepository,
            RedisAnalysisRetentionStore redis,
            AnalysisStreamProperties properties,
            AnalysisStreamMetrics metrics,
            PlatformTransactionManager transactionManager
    ) {
        this.requestRepository = requestRepository;
        this.cancellationRepository = cancellationRepository;
        this.redis = redis;
        this.properties = properties;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${analysis.stream.retention-poll-interval:PT5M}")
    public void sweep() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getRetentionAge());
        List<Long> candidates = requestRepository.findRetentionCandidateIds(
                AnalysisRequestOutbox.RETENTION_PROTOCOL_VERSION,
                OUTBOX_TERMINAL,
                ANALYSIS_TERMINAL,
                AnalysisCancellationOutboxStatus.PENDING,
                cutoff,
                PageRequest.of(0, properties.getRetentionBatchSize())
        );
        for (Long id : candidates) {
            try {
                transactionTemplate.executeWithoutResult(status -> clean(id, cutoff));
            } catch (RuntimeException error) {
                metrics.retentionFailed();
                log.warn("analysis stream retention failed: outboxId={}", id);
            }
        }
    }

    private void clean(Long id, OffsetDateTime cutoff) {
        AnalysisRequestOutbox event = requestRepository.findForRetentionById(id).orElse(null);
        if (!isStillEligible(event, cutoff)) {
            return;
        }
        Optional<AnalysisCancellationOutbox> cancellation = cancellationRepository
                .findByRequestEventId(event.getEventId());
        if (cancellation.isPresent()
                && cancellation.get().getStatus() != AnalysisCancellationOutboxStatus.PUBLISHED) {
            return;
        }
        UUID eventId = UUID.fromString(event.getEventId());
        Optional<String> indexed = redis.indexedStreamId(eventId);
        if (event.getRequestStreamId() != null
                && indexed.isPresent()
                && !event.getRequestStreamId().equals(indexed.get())) {
            throw new IllegalStateException("analysis_request_index_mismatch");
        }
        String streamId = event.getRequestStreamId();
        if (streamId == null && indexed.isPresent()) {
            streamId = indexed.get();
        }
        if (streamId != null && redis.requestEntryExists(streamId)) {
            return;
        }
        redis.deleteMarkers(eventId);
        cancellation.ifPresent(cancellationRepository::delete);
        requestRepository.delete(event);
        metrics.retentionCleaned();
    }

    private static boolean isStillEligible(AnalysisRequestOutbox event, OffsetDateTime cutoff) {
        return event != null
                && Integer.valueOf(AnalysisRequestOutbox.RETENTION_PROTOCOL_VERSION)
                        .equals(event.getRetentionProtocolVersion())
                && OUTBOX_TERMINAL.contains(event.getStatus())
                && ANALYSIS_TERMINAL.contains(event.getAnalysisResult().getStatus())
                && !event.getCreatedAt().isAfter(cutoff);
    }
}
