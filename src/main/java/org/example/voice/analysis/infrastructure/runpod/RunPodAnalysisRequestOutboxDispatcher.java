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
        Delivery delivery=transactions.execute(tx -> outboxRepository
                .findFirstByTransportAndStatusOrderByIdAsc("RUNPOD_HTTP",AnalysisRequestOutboxStatus.PENDING)
                .map(event -> {
                    org.example.voice.common.logging.AnalysisLogEvents.event(log,"analysis_dispatch_attempt",
                            event.getAnalysisResult().getId(),event.getEventId(),event.getExecutionId(),null)
                            .addKeyValue("queue_age_ms",java.time.Duration.between(event.getCreatedAt(),OffsetDateTime.now(ZoneOffset.UTC)).toMillis())
                            .log("Analysis dispatch attempt");
                    return new Delivery(event.getId(),event.getAnalysisResult().getId(),event.getPayload(),OffsetDateTime.now(ZoneOffset.UTC));
                })
                .orElse(null));
        if(delivery==null)return;
        String error=null;
        try { client.submit(codec.decodeRequest(delivery.payload())); }
        catch(RunPodAnalysisDeliveryException failure) { error=failure.code(); }
        catch(RuntimeException failure) { error=FAILURE; }
        final String outcome=error;
        transactions.execute(tx -> finish(delivery,outcome,true,0));
    }

    private OffsetDateTime finish(Delivery delivery, String error, boolean retryable, int retryAfter) {
        var event=outboxRepository.findForDeliveryUpdate(delivery.id()).orElse(null);
        if(event==null)return OffsetDateTime.now(ZoneOffset.UTC);
        if(error==null) {
            event.markDelivered(event.getExecutionId());
            org.example.voice.common.logging.AnalysisLogEvents.afterCommit(log,"analysis_dispatch_finished",
                    delivery.analysisId(),event.getEventId(),event.getExecutionId(),null);
        } else {
            event.recordDispatchFailure(error,Integer.MAX_VALUE);
            org.example.voice.common.logging.AnalysisLogEvents.afterCommit(log,"analysis_dispatch_failed",
                    delivery.analysisId(),event.getEventId(),event.getExecutionId(),error);
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private record Delivery(Long id, Long analysisId, String payload, OffsetDateTime readyAt) {}
}
