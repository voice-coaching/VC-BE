package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.domain.port.AnalysisResultWriter;
import org.example.voice.analysis.domain.port.AnalysisSegmentWriter;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.example.voice.training.domain.type.RecordingDeletionReason;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies an AI result only when it belongs to the currently active retry generation. */
@Service
@RequiredArgsConstructor
public class AnalysisResultIngestionService {

    private static final String DELIVERY_EXHAUSTED_CODE = "analysis_result_retry_exhausted";
    private static final String DELIVERY_EXHAUSTED_REASON = "분석 결과를 안전하게 반영하지 못했습니다. 다시 시도해 주세요.";

    private final AnalysisResultReader analysisResultReader;
    private final AnalysisResultWriter analysisResultWriter;
    private final AnalysisSegmentWriter analysisSegmentWriter;
    private final RecordingDeletionScheduler recordingDeletionScheduler;

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = AnalysisCacheNames.DETAIL, allEntries = true),
            @CacheEvict(cacheNames = AnalysisCacheNames.SESSION_RESULT, allEntries = true),
            @CacheEvict(cacheNames = AnalysisCacheNames.SEGMENTS, allEntries = true)
    })
    public AnalysisResultIngestionDisposition ingest(AnalysisWorkerResult message) {
        AnalysisResult analysisResult = analysisResultReader.findForIngestion(message.analysisId())
                .orElseThrow(() -> new IllegalArgumentException("analysis result does not exist"));
        if (!analysisResult.isForActiveRequest(message.requestEventId())) {
            return AnalysisResultIngestionDisposition.IGNORED_STALE;
        }

        return switch (message.status()) {
            case PROCESSING -> {
                if (!analysisResult.markProcessing()) {
                    yield AnalysisResultIngestionDisposition.IGNORED_DUPLICATE;
                }
                analysisResultWriter.save(analysisResult);
                yield AnalysisResultIngestionDisposition.APPLIED;
            }
            case COMPLETED -> {
                if (!analysisResult.complete(message)) {
                    yield AnalysisResultIngestionDisposition.IGNORED_DUPLICATE;
                }
                analysisResultWriter.save(analysisResult);
                analysisSegmentWriter.replaceForAnalysis(analysisResult, message.segments());
                scheduleVisualDeletion(analysisResult);
                yield AnalysisResultIngestionDisposition.APPLIED;
            }
            case FAILED -> {
                boolean failed = analysisResult.fail(
                        message.failureCode(),
                        message.failureReason(),
                        message.workerRevision(),
                        message.pipelineRevision()
                );
                analysisResultWriter.save(analysisResult);
                yield failed
                        ? AnalysisResultIngestionDisposition.APPLIED
                        : AnalysisResultIngestionDisposition.IGNORED_DUPLICATE;
            }
            case PENDING -> throw new IllegalArgumentException("PENDING is not a worker result status");
        };
    }

    private void scheduleVisualDeletion(AnalysisResult analysisResult) {
        var recording = analysisResult.getRecording();
        if (recording == null || recording.getVisualObjectKey() == null) {
            return;
        }
        recordingDeletionScheduler.schedule(
                recording.getTrainingSession().getUserId(),
                recording.getTrainingSession().getId(),
                recording.getVisualObjectKey(),
                RecordingDeletionReason.ANALYSIS_COMPLETED
        );
    }

    /**
     * Fails only the active generation after a valid result event has exhausted
     * consumer delivery attempts. A later retry uses a different request event
     * id, so an old pending record can never fail the new attempt.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = AnalysisCacheNames.DETAIL, allEntries = true),
            @CacheEvict(cacheNames = AnalysisCacheNames.SESSION_RESULT, allEntries = true),
            @CacheEvict(cacheNames = AnalysisCacheNames.SEGMENTS, allEntries = true)
    })
    public AnalysisResultIngestionDisposition failAfterDeliveryExhausted(AnalysisWorkerResult message) {
        AnalysisResult analysisResult = analysisResultReader.findForIngestion(message.analysisId())
                .orElseThrow(() -> new IllegalArgumentException("analysis result does not exist"));
        if (!analysisResult.isForActiveRequest(message.requestEventId())) {
            return AnalysisResultIngestionDisposition.IGNORED_STALE;
        }
        boolean failed = analysisResult.fail(
                DELIVERY_EXHAUSTED_CODE,
                DELIVERY_EXHAUSTED_REASON,
                message.workerRevision(),
                message.pipelineRevision()
        );
        if (!failed) {
            return AnalysisResultIngestionDisposition.IGNORED_DUPLICATE;
        }
        analysisResultWriter.save(analysisResult);
        return AnalysisResultIngestionDisposition.APPLIED;
    }
}
