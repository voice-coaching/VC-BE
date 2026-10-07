package org.example.voice.analysis.infrastructure.runpod;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.port.AnalysisCancellationSignal;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis", name = "transport", havingValue = "runpod_http")
public class RunPodExecutionTimeoutSweeper {
    private final AnalysisResultJpaRepository results;
    private final AnalysisRequestOutboxJpaRepository outboxes;
    private final AnalysisCancellationSignal cancellations;
    private final RunPodAnalysisProperties properties;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalDeliverySpool delivery;

    @Transactional
    @Scheduled(fixedDelayString = "${analysis.runpod.timeout-sweep-interval:PT15S}")
    public void expire() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var statuses=List.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING);
        var page=PageRequest.of(0, properties.getBatchSize());
        var received=delivery.enabled()?delivery.entries().stream().filter(e->e.receivedAt!=null && !e.rejected)
                .map(e->e.document.projection().identity().executionId().toString()).toList():List.<String>of();
        var expired=received.isEmpty()?results.findExpiredHttpForUpdate(statuses,now,page)
                :results.findExpiredHttpExcludingReceivedForUpdate(statuses,now,received,page);
        for (var result : expired) {
            if(delivery.current(result)!=null)continue; // Durable receipt moved responsibility to Backend.
            result.fail("analysis_execution_timeout", "분석 실행 시간이 만료되었습니다. 다시 시도해 주세요.", null, null);
            outboxes.findByAnalysisResultIdAndStatus(result.getId(), AnalysisRequestOutboxStatus.PENDING)
                    .forEach(o -> o.cancelPending("analysis_execution_timeout"));
            cancellations.schedule(UUID.fromString(result.getActiveRequestEventId()));
        }
    }
}
