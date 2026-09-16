package org.example.voice.analysis.infrastructure.runpod;

import org.example.voice.analysis.domain.entity.*;
import org.example.voice.analysis.domain.port.AnalysisCancellationSignal;
import org.example.voice.analysis.domain.type.*;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.OffsetDateTime;
import java.util.List;
import static org.example.voice.analysis.infrastructure.runpod.RunPodContractTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunPodExecutionTimeoutSweeperTest {
    @Test
    void expiredExecutionStopsPendingDeliveryAndSchedulesCancellation() {
        var results = mock(AnalysisResultJpaRepository.class);
        var outboxes = mock(AnalysisRequestOutboxJpaRepository.class);
        var cancellations = mock(AnalysisCancellationSignal.class);
        var result = AnalysisResult.pending(mock(VoiceRecording.class), REQUEST);
        ReflectionTestUtils.setField(result, "id", 1L);
        result.assignExecution(EXECUTION, OffsetDateTime.now().minusMinutes(1));
        var event = AnalysisRequestOutbox.pendingHttp(REQUEST, EXECUTION, result, "{}");
        when(results.findExpiredHttpForUpdate(any(), any(), any())).thenReturn(List.of(result));
        when(outboxes.findByAnalysisResultIdAndStatus(1L, AnalysisRequestOutboxStatus.PENDING)).thenReturn(List.of(event));
        new RunPodExecutionTimeoutSweeper(results, outboxes, cancellations, new RunPodAnalysisProperties()).expire();
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(event.getStatus()).isEqualTo(AnalysisRequestOutboxStatus.FAILED);
        verify(cancellations).schedule(REQUEST);
    }
}
