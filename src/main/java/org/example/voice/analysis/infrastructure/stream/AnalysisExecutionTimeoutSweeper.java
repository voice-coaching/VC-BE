package org.example.voice.analysis.infrastructure.stream;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.analysis.domain.port.AnalysisCancellationSignal;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisExecutionTimeoutSweeper {

    static final String TIMEOUT_CODE = "analysis_execution_timeout";
    static final String TIMEOUT_REASON = "분석 처리 시간이 초과되었습니다. 다시 시도해 주세요.";

    private final AnalysisResultJpaRepository resultRepository;
    private final AnalysisRequestOutboxJpaRepository outboxRepository;
    private final ProcessingConsentLedger consentLedger;
    private final AnalysisStreamProperties properties;
    private final AnalysisStreamMetrics metrics;
    private final AnalysisCancellationSignal cancellationSignal;

    @Scheduled(fixedDelayString = "${analysis.stream.timeout-sweep-interval:PT1M}")
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = AnalysisCacheNames.DETAIL, allEntries = true),
            @CacheEvict(cacheNames = AnalysisCacheNames.SESSION_RESULT, allEntries = true),
            @CacheEvict(cacheNames = AnalysisCacheNames.SEGMENTS, allEntries = true)
    })
    public void failStaleAnalyses() {}
}
