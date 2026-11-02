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
    public void expire() {}
}
