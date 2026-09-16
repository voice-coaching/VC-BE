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

    public RunPodAnalysisRequestOutboxDispatcher(AnalysisRequestOutboxJpaRepository outboxRepository,
            AnalysisResultJpaRepository analysisResultRepository, RunPodAnalysisClient client,
            RunPodAnalysisPayloadCodec codec, RunPodAnalysisProperties properties, PlatformTransactionManager manager) {
        this.outboxRepository = outboxRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.client = client;
        this.codec = codec;
        this.properties = properties;
        this.transactions = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${analysis.runpod.outbox-poll-interval:PT1S}")
    public void dispatchPending() {
        for (int i = 0; i < properties.getBatchSize(); i++) {
            Delivery delivery = transactions.execute(status -> outboxRepository
                    .findFirstByTransportAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                            "RUNPOD_HTTP", AnalysisRequestOutboxStatus.PENDING, OffsetDateTime.now(ZoneOffset.UTC))
                    .map(event -> {
                        event.reserveDeliveryUntil(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30));
                        return new Delivery(event.getId(), event.getAnalysisResult().getId(), event.getPayload());
                    }).orElse(null));
            if (delivery == null) return;
            String error = null;
            boolean retryable = true;
            try {
                var request = codec.decodeRequest(delivery.payload());
                var accepted = client.submit(request);
                if (accepted == null || !request.requestId().equals(accepted.requestId())
                        || !request.executionId().equals(accepted.executionId()) || accepted.workerInstanceId() == null) {
                    throw new RunPodAnalysisDeliveryException("runpod_acceptance_contract_invalid", false, null);
                }
            } catch (RunPodAnalysisDeliveryException failure) {
                error = failure.code();
                retryable = failure.isRetryable();
            } catch (RunPodContractException failure) {
                error = "runpod_contract_invalid";
                retryable = false;
            } catch (RuntimeException failure) {
                error = FAILURE;
            }
            final String outcome = error;
            final boolean retry = retryable;
            transactions.executeWithoutResult(status -> finish(delivery, outcome, retry));
        }
    }

    private void finish(Delivery delivery, String error, boolean retryable) {
        // Match cancellation/result lock order: analysis first, then outbox.
        var result = analysisResultRepository.findForIngestion(delivery.analysisId()).orElse(null);
        var event = outboxRepository.findForDeliveryUpdate(delivery.id()).orElse(null);
        if (result == null || event == null || event.getStatus() != AnalysisRequestOutboxStatus.PENDING) return;
        if (!result.isForActiveRequest(UUID.fromString(event.getEventId()))
                || !result.isForActiveExecution(UUID.fromString(event.getExecutionId()))) {
            event.cancelPending("stale_execution");
            return;
        }
        // A claim proves delivery even if its HTTP acknowledgment was lost; never fail live inference.
        if (error == null || result.getWorkerInstanceId() != null) {
            event.markDelivered(event.getExecutionId());
        } else {
            log.warn("runpod delivery failed: eventId={}, code={}", event.getEventId(), error);
            if (event.recordDispatchFailure(error, retryable ? properties.getDispatchMaxAttempts() : 1)) {
                result.fail(FAILURE, "분석 작업을 전달하지 못했습니다. 다시 시도해 주세요.", null, null);
            }
        }
    }

    private record Delivery(Long id, Long analysisId, String payload) {}
}