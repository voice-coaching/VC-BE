package org.example.voice.analysis.infrastructure.runpod;

import org.example.voice.analysis.domain.entity.*;
import org.example.voice.analysis.domain.type.*;
import org.example.voice.analysis.infrastructure.*;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.example.voice.analysis.infrastructure.runpod.RunPodContractTest.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RunPodDispatchTest {
    private static class Transactions extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) {}
        protected void doRollback(DefaultTransactionStatus status) {}
    }

    @Test
    void httpRunsOutsideTransactionAndLostAckDoesNotFailClaimedJob() {
        var requests = mock(AnalysisRequestOutboxJpaRepository.class);
        var results = mock(AnalysisResultJpaRepository.class);
        var client = mock(RunPodAnalysisClient.class);
        var recording = mock(VoiceRecording.class);
        var result = AnalysisResult.pending(recording, REQUEST);
        ReflectionTestUtils.setField(result, "id", 1L);
        result.assignExecution(EXECUTION);
        var event = AnalysisRequestOutbox.pendingHttp(REQUEST, EXECUTION, result, payload(OffsetDateTime.now().plusMinutes(5)));
        ReflectionTestUtils.setField(event, "id", 4L);
        when(requests.findFirstByTransportAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(any(), any(), any()))
                .thenReturn(Optional.of(event), Optional.empty());
        when(requests.findForDeliveryUpdate(4L)).thenReturn(Optional.of(event));
        when(results.findForIngestion(1L)).thenReturn(Optional.of(result));
        when(client.submit(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            result.claim(REQUEST, EXECUTION, WORKER.toString(), OffsetDateTime.now().plusSeconds(90));
            throw new RunPodAnalysisDeliveryException("runpod_http_io_error", true, null);
        });
        new RunPodAnalysisRequestOutboxDispatcher(requests, results, client, new RunPodAnalysisPayloadCodec(),
                new RunPodAnalysisProperties(), new Transactions()).dispatchPending();
        assertThat(event.getStatus()).isEqualTo(AnalysisRequestOutboxStatus.PUBLISHED);
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(result.getFailureCode()).isNull();
    }

    @Test
    void cancellationUsesOriginalExecutionAndDoesNotHoldTransactionDuringHttp() {
        var requests = mock(AnalysisRequestOutboxJpaRepository.class);
        var cancellations = mock(AnalysisCancellationOutboxJpaRepository.class);
        var client = mock(RunPodAnalysisClient.class);
        var result = AnalysisResult.pending(mock(VoiceRecording.class), REQUEST);
        var original = AnalysisRequestOutbox.pendingHttp(REQUEST, EXECUTION, result, "{}");
        result.retry(UUID.randomUUID());
        result.assignExecution(UUID.randomUUID());
        var event = AnalysisCancellationOutbox.pending(REQUEST);
        when(cancellations.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(any(), any()))
                .thenReturn(Optional.of(event), Optional.empty());
        when(cancellations.findByRequestEventId(REQUEST.toString())).thenReturn(Optional.of(event));
        when(requests.findByEventIdAndTransport(REQUEST.toString(), "RUNPOD_HTTP")).thenReturn(Optional.of(original));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(client).cancel(REQUEST, EXECUTION);
        new RunPodAnalysisCancellationOutboxDispatcher(cancellations, requests, client, new RunPodAnalysisProperties(),
                new Transactions()).dispatchPending();
        verify(client).cancel(REQUEST, EXECUTION);
        assertThat(event.getStatus()).isEqualTo(AnalysisCancellationOutboxStatus.PUBLISHED);
    }
}
