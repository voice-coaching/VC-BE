package org.example.voice.analysis.infrastructure.stream;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
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

    @Scheduled(fixedDelayString = "${analysis.stream.timeout-sweep-interval:PT1M}")
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = AnalysisCacheNames.DETAIL, allEntries = true),
            @CacheEvict(cacheNames = AnalysisCacheNames.SESSION_RESULT, allEntries = true),
            @CacheEvict(cacheNames = AnalysisCacheNames.SEGMENTS, allEntries = true)
    })
    public void failStaleAnalyses() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getExecutionTimeout());
        List<AnalysisResult> stale = resultRepository.findStaleForUpdate(
                List.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING),
                cutoff,
                PageRequest.of(0, properties.getTimeoutSweepBatchSize())
        );
        for (AnalysisResult result : stale) {
            outboxRepository.findByAnalysisResultIdAndStatus(
                    result.getId(), AnalysisRequestOutboxStatus.PENDING
            ).forEach(outbox -> outbox.cancelPending(TIMEOUT_CODE));
            if (result.fail(TIMEOUT_CODE, TIMEOUT_REASON, null, null)) {
                consentLedger.revokeForSession(
                        result.getRecording().getTrainingSession().getUserId(),
                        result.getRecording().getTrainingSession().getId()
                );
            }
        }
    }
}
