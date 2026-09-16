package org.example.voice.analysis.infrastructure.runpod;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.type.AnalysisCancellationOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisCancellationOutboxJpaRepository;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "analysis", name = "transport", havingValue = "runpod_http")
public class RunPodAnalysisCancellationOutboxDispatcher {
    private final AnalysisCancellationOutboxJpaRepository cancellations;
    private final AnalysisRequestOutboxJpaRepository requests;
    private final RunPodAnalysisClient client;
    private final RunPodAnalysisProperties properties;
    private final TransactionTemplate transactions;

    public RunPodAnalysisCancellationOutboxDispatcher(AnalysisCancellationOutboxJpaRepository cancellations,
            AnalysisRequestOutboxJpaRepository requests, RunPodAnalysisClient client,
            RunPodAnalysisProperties properties, PlatformTransactionManager manager) {
        this.cancellations = cancellations;
        this.requests = requests;
        this.client = client;
        this.properties = properties;
        this.transactions = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${analysis.runpod.outbox-poll-interval:PT1S}")
    public void dispatchPending() {
        for (int i = 0; i < properties.getBatchSize(); i++) {
            Delivery delivery = transactions.execute(status -> cancellations
                    .findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                            AnalysisCancellationOutboxStatus.PENDING, OffsetDateTime.now(ZoneOffset.UTC))
                    .map(event -> {
                        event.reserveDeliveryUntil(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30));
                        // Bind cancellation to the immutable original outbox, never the current retry generation.
                        var original = requests.findByEventIdAndTransport(event.getRequestEventId(), "RUNPOD_HTTP");
                        return new Delivery(event.getRequestEventId(), original.map(o -> o.getExecutionId()).orElse(null));
                    }).orElse(null));
            if (delivery == null) return;
            String error = null;
            try {
                if (delivery.executionId() != null) client.cancel(UUID.fromString(delivery.requestId()), UUID.fromString(delivery.executionId()));
            } catch (RunPodAnalysisDeliveryException failure) {
                error = failure.code();
            } catch (RuntimeException failure) {
                error = "runpod_cancellation_delivery_failed";
            }
            final String outcome = error;
            transactions.executeWithoutResult(status -> cancellations.findByRequestEventId(delivery.requestId())
                    .filter(e -> e.getStatus() == AnalysisCancellationOutboxStatus.PENDING)
                    .ifPresent(event -> {
                        if (outcome == null) event.markPublished();
                        else event.recordDeliveryFailure(outcome);
                    }));
        }
    }

    private record Delivery(String requestId, String executionId) {}
}