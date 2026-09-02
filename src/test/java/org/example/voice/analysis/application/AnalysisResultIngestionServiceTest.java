package org.example.voice.analysis.application;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerPronunciationEvidence;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.domain.port.AnalysisResultWriter;
import org.example.voice.analysis.domain.port.AnalysisSegmentWriter;
import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisResultIngestionDisposition;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisResultIngestionServiceTest {

    @Mock private AnalysisResultReader reader;
    @Mock private AnalysisResultWriter writer;
    @Mock private AnalysisSegmentWriter segmentWriter;
    @Mock private AnalysisResult analysisResult;

    @Test
    void ignoresLateResultFromPreviousRetryGeneration() {
        AnalysisWorkerResult result = completed();
        when(reader.findForIngestion(1L)).thenReturn(Optional.of(analysisResult));
        when(analysisResult.isForActiveRequest(result.requestEventId())).thenReturn(false);

        AnalysisResultIngestionDisposition disposition = service().ingest(result);

        assertThat(disposition).isEqualTo(AnalysisResultIngestionDisposition.IGNORED_STALE);
        verify(writer, never()).save(analysisResult);
        verify(segmentWriter, never()).replaceForAnalysis(analysisResult, result.segments());
    }

    @Test
    void persistsSegmentsOnlyForTheFirstAcceptedCompletedResult() {
        AnalysisWorkerResult result = completed();
        when(reader.findForIngestion(1L)).thenReturn(Optional.of(analysisResult));
        when(analysisResult.isForActiveRequest(result.requestEventId())).thenReturn(true);
        when(analysisResult.complete(result)).thenReturn(true);

        AnalysisResultIngestionDisposition disposition = service().ingest(result);

        assertThat(disposition).isEqualTo(AnalysisResultIngestionDisposition.APPLIED);
        verify(writer).save(analysisResult);
        verify(segmentWriter).replaceForAnalysis(analysisResult, result.segments());
    }

    @Test
    void failsOnlyTheActiveGenerationWhenResultDeliveryIsExhausted() {
        AnalysisWorkerResult result = completed();
        when(reader.findForIngestion(1L)).thenReturn(Optional.of(analysisResult));
        when(analysisResult.isForActiveRequest(result.requestEventId())).thenReturn(true);
        when(analysisResult.fail(
                "analysis_result_retry_exhausted",
                "분석 결과를 안전하게 반영하지 못했습니다. 다시 시도해 주세요.",
                "worker-v1",
                "pipeline-v1"
        )).thenReturn(true);

        AnalysisResultIngestionDisposition disposition = service().failAfterDeliveryExhausted(result);

        assertThat(disposition).isEqualTo(AnalysisResultIngestionDisposition.APPLIED);
        verify(writer).save(analysisResult);
        verify(segmentWriter, never()).replaceForAnalysis(analysisResult, result.segments());
    }

    private AnalysisResultIngestionService service() {
        return new AnalysisResultIngestionService(reader, writer, segmentWriter);
    }

    private AnalysisWorkerResult completed() {
        return new AnalysisWorkerResult(
                AnalysisWorkerResult.SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                AnalysisStatus.COMPLETED,
                AnalysisOutcome.COACHING_READY,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "‘가’의 첫 번째 음절 ‘가’에 해당하는 목표 음소 ‘ㄱ’ 소리를 다시 연습해 보세요.",
                new AnalysisWorkerPronunciationEvidence(
                        AnalysisWorkerPronunciationEvidence.SCHEMA_VERSION,
                        "ㄱ",
                        0,
                        100,
                        200,
                        new BigDecimal("0.91"),
                        new BigDecimal("0.80"),
                        AnalysisWorkerPronunciationEvidence.SCORE_SEMANTICS,
                        AnalysisWorkerPronunciationEvidence.EVIDENCE_STATE
                ),
                "worker-v1",
                "pipeline-v1",
                "a".repeat(64),
                List.of()
        );
    }
}
