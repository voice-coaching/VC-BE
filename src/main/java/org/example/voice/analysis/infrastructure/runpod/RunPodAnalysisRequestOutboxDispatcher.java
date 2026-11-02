package org.example.voice.analysis.infrastructure.runpod;

import lombok.extern.slf4j.Slf4j;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "analysis", name = "transport", havingValue = "runpod_http")
public class RunPodAnalysisRequestOutboxDispatcher {
    private static final String FAILURE = "runpod_analysis_request_delivery_failed";
    private final AnalysisRequestOutboxJpaRepository outboxRepository;
    private final AnalysisResultJpaRepository analysisResultRepository;
    private final RunPodAnalysisClient client;
    private final RunPodAnalysisPayloadCodec codec;
    private final RunPodAnalysisProperties properties;
    private final TransactionTemplate transactions;
    private final RunPodDispatchGate gate;

    public RunPodAnalysisRequestOutboxDispatcher(AnalysisRequestOutboxJpaRepository outboxRepository,
            AnalysisResultJpaRepository analysisResultRepository, RunPodAnalysisClient client,
            RunPodAnalysisPayloadCodec codec, RunPodAnalysisProperties properties, PlatformTransactionManager manager, RunPodDispatchGate gate) {
        this.outboxRepository = outboxRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.client = client;
        this.codec = codec;
        this.properties = properties;
        this.gate = gate;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setTimeout(5);
    }

    @Scheduled(fixedDelayString = "${analysis.runpod.outbox-poll-interval:PT1S}")
    public void dispatchPending() {
        if (!properties.isConfigured()) return;
        var claim = gate.acquire(properties.normalizedEndpointUrl());
        if (claim == null) return;
        try {
            Delivery delivery = transactions.execute(status -> outboxRepository
                    .findFirstByTransportAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                            "RUNPOD_HTTP", AnalysisRequestOutboxStatus.PENDING, OffsetDateTime.now(ZoneOffset.UTC))
                    .map(event -> {
                        var now = OffsetDateTime.now(ZoneOffset.UTC);
                        var analysis = event.getAnalysisResult();
                        if (analysis.getStatus() == org.example.voice.analysis.domain.type.AnalysisStatus.COMPLETED
                                || analysis.getStatus() == org.example.voice.analysis.domain.type.AnalysisStatus.FAILED
                                || !analysis.isForActiveRequest(UUID.fromString(event.getEventId()))
                                || !analysis.isForActiveExecution(UUID.fromString(event.getExecutionId()))) {
                            event.cancelPending("stale_or_terminal_execution");
                            return null;
                        }
                        event.reserveDeliveryUntil(now.plusSeconds(60));
                        org.example.voice.common.logging.AnalysisLogEvents.event(log,"analysis_dispatch_attempt",analysis.getId(),event.getEventId(),event.getExecutionId(),null)
                                .addKeyValue("queue_age_ms",java.time.Duration.between(event.getCreatedAt(),now).toMillis()).log("Analysis dispatch attempt");
                        return new Delivery(event.getId(), event.getAnalysisResult().getId(), event.getPayload(),event.getEventId(),event.getExecutionId());
                    }).orElse(null));
            if (delivery == null) return;
            String error = null;
            boolean retryable = true;
            int retryAfter = 1;
            long started = System.nanoTime();
            try {
                var request = codec.decodeRequest(delivery.payload());
                if (!request.deadlineAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
                    error = "analysis_execution_timeout";
                    retryable = false;
                } else {
                    var accepted = client.submit(request);
                    if (accepted == null || !request.requestId().equals(accepted.requestId())
                            || !request.executionId().equals(accepted.executionId()) || accepted.workerInstanceId() == null)
                        throw new RunPodAnalysisDeliveryException("runpod_acceptance_contract_invalid", false, null);
                }
            } catch (RunPodAnalysisDeliveryException failure) {
                error = failure.code(); retryable = failure.isRetryable(); retryAfter = failure.retryAfterSeconds();
            } catch (RunPodContractException failure) {
                error = "runpod_contract_invalid"; retryable = false;
            } catch (RuntimeException failure) { error = FAILURE; }
            final String outcome = error;
            final boolean retry = retryable;
            final int delay = retryAfter;
            transactions.execute(status -> finish(delivery, outcome, retry, delay));
            org.example.voice.common.logging.AnalysisLogEvents.event(log,"analysis_dispatch_finished",delivery.analysisId(),delivery.requestId(),delivery.executionId(),outcome)
                    .addKeyValue("duration_ms",(System.nanoTime()-started)/1_000_000).log("Analysis dispatch finished");
        } finally {
            // Backoff belongs to the individual outbox row. Holding the endpoint
            // until that row is due would block unrelated, ready requests.
            gate.release(claim, OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    private OffsetDateTime finish(Delivery delivery, String error, boolean retryable, int retryAfter) {
        // Match cancellation/result lock order: analysis first, then outbox.
        var result = analysisResultRepository.findForIngestion(delivery.analysisId()).orElse(null);
        var event = outboxRepository.findForDeliveryUpdate(delivery.id()).orElse(null);
        if (result == null || event == null || event.getStatus() != AnalysisRequestOutboxStatus.PENDING) return OffsetDateTime.now(ZoneOffset.UTC);
        if (!result.isForActiveRequest(UUID.fromString(event.getEventId()))
                || !result.isForActiveExecution(UUID.fromString(event.getExecutionId()))) {
            event.cancelPending("stale_execution");
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (result.getStatus() == org.example.voice.analysis.domain.type.AnalysisStatus.COMPLETED
                || result.getStatus() == org.example.voice.analysis.domain.type.AnalysisStatus.FAILED) {
            event.cancelPending("terminal_execution");
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        if ("runpod_capacity_busy".equals(error) && result.getWorkerInstanceId() == null) {
            org.example.voice.common.logging.AnalysisLogEvents.event(log,"analysis_capacity_wait",delivery.analysisId(),event.getEventId(),event.getExecutionId(),null).log("Analysis capacity wait");
            return event.deferCapacity(retryAfter);
        }
        // A claim proves delivery even if its HTTP acknowledgment was lost; never fail live inference.
        if (error == null || result.getWorkerInstanceId() != null) {
            event.markDelivered(event.getExecutionId());
        } else {
            org.example.voice.common.logging.AnalysisLogEvents.event(log,"analysis_dispatch_failed",delivery.analysisId(),event.getEventId(),event.getExecutionId(),error).log("Analysis dispatch failed");
            if (event.recordDispatchFailure(error, retryable ? properties.getDispatchMaxAttempts() : 1, retryAfter)) {
                boolean expired = "analysis_execution_timeout".equals(error);
                result.fail(expired ? error : FAILURE, expired ? "분석 제한 시간이 지났습니다. 다시 시도해 주세요."
                        : "분석 작업을 전달하지 못했습니다. 다시 시도해 주세요.", null, null);
                org.example.voice.common.logging.AnalysisLogEvents.afterCommit(log,"analysis_failed",result.getId(),result.getActiveRequestEventId(),result.getActiveExecutionId(),expired?error:FAILURE);
            }
        }
        return event.getStatus() == AnalysisRequestOutboxStatus.PENDING
                ? event.getNextAttemptAt() : OffsetDateTime.now(ZoneOffset.UTC);
    }

    private record Delivery(Long id, Long analysisId, String payload,String requestId,String executionId) {}
}
