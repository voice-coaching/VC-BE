package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.port.AnalysisCancellation;
import org.example.voice.analysis.domain.port.AnalysisCancellationSignal;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaAnalysisCancellation implements AnalysisCancellation {

    static final String CANCELED_CODE = "analysis_session_canceled";
    static final String CANCELED_REASON = "학습 세션이 취소되어 분석 결과를 폐기했습니다.";

    private final AnalysisResultJpaRepository resultRepository;
    private final AnalysisRequestOutboxJpaRepository outboxRepository;
    private final AnalysisSegmentJpaRepository segmentRepository;
    private final AnalysisCancellationSignal cancellationSignal;

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = AnalysisCacheNames.DETAIL, allEntries = true),
            @CacheEvict(cacheNames = AnalysisCacheNames.SESSION_RESULT, allEntries = true),
            @CacheEvict(cacheNames = AnalysisCacheNames.SEGMENTS, allEntries = true)
    })
    public void cancelForSession(Long sessionId) {
        cancel(resultRepository.findCancelableForUpdate(sessionId, AnalysisStatus.FAILED));
    }

    @Override
    public void cancelForUser(Long userId) {
        cancel(resultRepository.findCancelableForUserForUpdate(userId, AnalysisStatus.FAILED));
    }

    private void cancel(java.util.List<AnalysisResult> results) {
        for (AnalysisResult result : results) {
            outboxRepository.findByAnalysisResultIdOrderByIdAsc(result.getId()).forEach(outbox -> {
                outbox.cancelPending(CANCELED_CODE);
                cancellationSignal.schedule(UUID.fromString(outbox.getEventId()));
            });
            segmentRepository.deleteByAnalysisResultId(result.getId());
            result.cancel(CANCELED_CODE, CANCELED_REASON);
        }
    }
}
