package org.example.voice.analysis.infrastructure;

import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.port.AnalysisCancellationSignal;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaAnalysisCancellationTest {

    @Test
    void cancelsPendingDispatchAndClearsTheAnalysisGeneration() {
        AnalysisResult result = AnalysisResult.pending(mock(VoiceRecording.class), UUID.randomUUID());
        UUID requestEventId = UUID.randomUUID();
        AnalysisRequestOutbox event = AnalysisRequestOutbox.pending(requestEventId, result, "synthetic-payload");
        AnalysisResultJpaRepository results = mock(AnalysisResultJpaRepository.class);
        AnalysisRequestOutboxJpaRepository outbox = mock(AnalysisRequestOutboxJpaRepository.class);
        AnalysisSegmentJpaRepository segments = mock(AnalysisSegmentJpaRepository.class);
        when(results.findCancelableForUpdate(7L, AnalysisStatus.FAILED)).thenReturn(List.of(result));
        when(outbox.findByAnalysisResultIdOrderByIdAsc(null))
                .thenReturn(List.of(event));
        AnalysisCancellationSignal signal = mock(AnalysisCancellationSignal.class);

        new JpaAnalysisCancellation(results, outbox, segments, signal).cancelForSession(7L);

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getFailureCode()).isEqualTo(JpaAnalysisCancellation.CANCELED_CODE);
        assertThat(event.getStatus()).isEqualTo(AnalysisRequestOutboxStatus.FAILED);
        assertThat(event.getLastErrorCode()).isEqualTo(JpaAnalysisCancellation.CANCELED_CODE);
        verify(segments).deleteByAnalysisResultId(null);
        verify(signal).schedule(requestEventId);
    }
}
