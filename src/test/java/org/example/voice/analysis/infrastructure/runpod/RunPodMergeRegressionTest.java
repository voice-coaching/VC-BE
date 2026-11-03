package org.example.voice.analysis.infrastructure.runpod;

import org.example.voice.analysis.domain.entity.*;
import org.example.voice.analysis.domain.type.*;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.example.voice.analysis.infrastructure.runpod.RunPodContractTest.*;

/** Both branches' delivery behavior must survive the ancestry merge, without changing main policies. */
class RunPodMergeRegressionTest {
    static class Transactions extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction(){return new Object();}
        protected void doBegin(Object tx,TransactionDefinition definition){}
        protected void doCommit(DefaultTransactionStatus status){}
        protected void doRollback(DefaultTransactionStatus status){}
    }
    record Fixture(AnalysisResult result,AnalysisRequestOutbox event,RunPodAnalysisClient client,
                   RunPodDispatchGate gate,RunPodAnalysisRequestOutboxDispatcher dispatcher){}
    Fixture fixture(OffsetDateTime deadline){
        var repository=mock(AnalysisRequestOutboxJpaRepository.class);
        var results=mock(AnalysisResultJpaRepository.class);
        var client=mock(RunPodAnalysisClient.class);var gate=mock(RunPodDispatchGate.class);
        var result=AnalysisResult.pending(mock(VoiceRecording.class),REQUEST);result.assignExecution(EXECUTION);
        ReflectionTestUtils.setField(result,"id",1L);
        var event=AnalysisRequestOutbox.pendingHttp(REQUEST,EXECUTION,result,payload(deadline));
        ReflectionTestUtils.setField(event,"id",4L);
        when(repository.findFirstByTransportAndStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(eq("RUNPOD_HTTP"),eq(AnalysisRequestOutboxStatus.PENDING),any())).thenReturn(Optional.of(event));
        when(repository.findForDeliveryUpdate(4L)).thenReturn(Optional.of(event));
        when(results.findForIngestion(1L)).thenReturn(Optional.of(result));
        when(gate.acquire(anyString())).thenReturn(new RunPodDispatchGate.Claim("fixture",UUID.randomUUID()));
        var properties=new RunPodAnalysisProperties();properties.setEndpointUrl("https://worker.example.invalid");
        properties.setApiToken("fixture");properties.setCallbackToken("fixture");properties.setCallbackBaseUrl("https://backend.example.invalid");properties.setDispatchMaxAttempts(2);
        return new Fixture(result,event,client,gate,new RunPodAnalysisRequestOutboxDispatcher(repository,results,client,
                new RunPodAnalysisPayloadCodec(),properties,new Transactions(),gate));
    }
    @Test void retryAfterDefersOnlyItsRowAndReleasesEndpointImmediately(){
        var f=fixture(OffsetDateTime.now().plusMinutes(5));var before=OffsetDateTime.now();
        when(f.client().submit(any())).thenThrow(new RunPodAnalysisDeliveryException("runpod_http_io_error",true,null).retryAfter("120"));
        f.dispatcher().dispatchPending();
        assertThat(f.event().getStatus()).isEqualTo(AnalysisRequestOutboxStatus.PENDING);
        assertThat(f.event().getAttemptCount()).isEqualTo(1);
        assertThat(f.event().getNextAttemptAt()).isAfterOrEqualTo(before.plusSeconds(120));
        var released=ArgumentCaptor.forClass(OffsetDateTime.class);verify(f.gate()).release(any(),released.capture());
        assertThat(released.getValue()).isBefore(f.event().getNextAttemptAt());
        assertThat(f.result().getStatus()).isEqualTo(AnalysisStatus.PENDING);
    }
    @Test void retryableDeliveryStopsAtConfiguredAttemptLimit(){
        var f=fixture(OffsetDateTime.now().plusMinutes(5));
        f.event().recordDispatchFailure("runpod_http_io_error",2,1);
        when(f.client().submit(any())).thenThrow(new RunPodAnalysisDeliveryException("runpod_http_io_error",true,null));
        f.dispatcher().dispatchPending();
        assertThat(f.event().getAttemptCount()).isEqualTo(2);
        assertThat(f.event().getStatus()).isEqualTo(AnalysisRequestOutboxStatus.FAILED);
        assertThat(f.result().getStatus()).isEqualTo(AnalysisStatus.FAILED);
    }
    @Test void expiredOriginalNeverCallsWorker(){
        var f=fixture(OffsetDateTime.now().minusMinutes(1));f.dispatcher().dispatchPending();
        verifyNoInteractions(f.client());assertThat(f.result().getFailureCode()).isEqualTo("analysis_execution_timeout");
        assertThat(f.event().getStatus()).isEqualTo(AnalysisRequestOutboxStatus.FAILED);
    }
    @Test void staleGenerationIsRejectedEvenWithMainGlobalHelpersRelaxed(){
        var f=fixture(OffsetDateTime.now().plusMinutes(5));
        f.result().retry(UUID.randomUUID());f.result().assignExecution(UUID.randomUUID());
        f.dispatcher().dispatchPending();verifyNoInteractions(f.client());
        assertThat(f.event().getStatus()).isEqualTo(AnalysisRequestOutboxStatus.FAILED);
        assertThat(f.result().getStatus()).isEqualTo(AnalysisStatus.PENDING);
    }
    @Test void claimedAndHandoffReceivedJobsAreNotResubmitted(){
        for(boolean handoff:new boolean[]{false,true}){
            var f=fixture(OffsetDateTime.now().plusMinutes(5));
            ReflectionTestUtils.setField(f.result(),handoff?"handoffReceivedAt":"workerInstanceId",handoff?OffsetDateTime.now():WORKER.toString());
            f.dispatcher().dispatchPending();verifyNoInteractions(f.client());
            assertThat(f.event().getStatus()).isEqualTo(AnalysisRequestOutboxStatus.PUBLISHED);
        }
    }
    @Test void retryGenerationChangedDuringHttpDoesNotFailNewGeneration(){
        var f=fixture(OffsetDateTime.now().plusMinutes(5));
        when(f.client().submit(any())).thenAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            f.result().retry(UUID.randomUUID());f.result().assignExecution(UUID.randomUUID());
            throw new RunPodAnalysisDeliveryException("runpod_http_io_error",false,null);
        });
        f.dispatcher().dispatchPending();
        assertThat(f.event().getStatus()).isEqualTo(AnalysisRequestOutboxStatus.FAILED);
        assertThat(f.result().getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(f.result().getFailureCode()).isNull();
    }
    @Test void handoffReceivedDuringLostAcknowledgementIsPreserved(){
        var f=fixture(OffsetDateTime.now().plusMinutes(5));
        when(f.client().submit(any())).thenAnswer(call->{
            ReflectionTestUtils.setField(f.result(),"handoffReceivedAt",OffsetDateTime.now());
            throw new RunPodAnalysisDeliveryException("runpod_http_io_error",false,null);
        });
        f.dispatcher().dispatchPending();
        assertThat(f.event().getStatus()).isEqualTo(AnalysisRequestOutboxStatus.PUBLISHED);
        assertThat(f.result().getFailureCode()).isNull();
    }
}
