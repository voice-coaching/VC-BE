package org.example.voice.analysis.infrastructure.runpod;

import org.example.voice.analysis.domain.entity.*;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.domain.port.CanonicalExecutionRegistry;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.analysis.infrastructure.canonical.*;
import org.example.voice.common.exception.BaseException;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.domain.port.RecordingMediaPreparationStore;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnifiedV6PublisherTest {
    final AnalysisRequestOutboxJpaRepository outboxes=mock(AnalysisRequestOutboxJpaRepository.class);
    final AnalysisResultJpaRepository results=mock(AnalysisResultJpaRepository.class);
    final CanonicalHandoffSettings settings=mock(CanonicalHandoffSettings.class);
    final CanonicalRequestScope scope=mock(CanonicalRequestScope.class);
    final RecordingMediaPreparationStore preparations=mock(RecordingMediaPreparationStore.class);
    final CanonicalExecutionRegistry registry=mock(CanonicalExecutionRegistry.class);
    final CanonicalAudiovisualReadiness video=mock(CanonicalAudiovisualReadiness.class);
    final RunPodAnalysisProperties properties=mock(RunPodAnalysisProperties.class);
    final AnalysisWorkerRequest request=mock(AnalysisWorkerRequest.class);
    final VoiceRecording recording=mock(VoiceRecording.class);
    AnalysisResult result;
    RunPodOutboxAnalysisJobPublisher publisher;

    @BeforeEach void setup() {
        UUID event=UUID.randomUUID();
        result=AnalysisResult.pending(recording,event);
        ReflectionTestUtils.setField(result,"id",1L);
        when(recording.getId()).thenReturn(2L);
        when(request.eventId()).thenReturn(event);
        when(request.analysisId()).thenReturn(1L);
        when(request.contentId()).thenReturn(3L);
        when(request.learningFocus()).thenReturn(LearningFocus.PRONUNCIATION);
        when(request.promptRevision()).thenReturn("test-v1");
        when(request.scriptText()).thenReturn("test");
        when(request.scriptSha256()).thenReturn("a".repeat(64));
        when(request.audioObjectKey()).thenReturn("recordings/analysis-audio/fixture.wav");
        when(request.audioSha256()).thenReturn("b".repeat(64));
        when(request.mimeType()).thenReturn("audio/wav");
        when(request.fileSizeBytes()).thenReturn(100L);
        when(request.durationMs()).thenReturn(1000);
        when(properties.isConfigured()).thenReturn(true);
        when(properties.getExecutionTimeout()).thenReturn(Duration.ofMinutes(5));
        when(properties.getMaximumPayloadBytes()).thenReturn(65536);
        when(results.findForIngestion(1L)).thenReturn(Optional.of(result));
        when(scope.eligible(result,false)).thenReturn(true);
        when(preparations.find(2L)).thenReturn(Optional.empty());
        publisher=new RunPodOutboxAnalysisJobPublisher(outboxes,results,new RunPodAnalysisPayloadCodec(),
                properties,registry,settings,scope,preparations,new RunPodContract(),video);
    }

    @Test void publishesAudioAsV6WithoutInventingVideoOrPreparation() {
        publisher.publish(request);
        var captured=ArgumentCaptor.forClass(AnalysisRequestOutbox.class);
        verify(outboxes).save(captured.capture());
        var payload=new RunPodAnalysisPayloadCodec().decodeRequest(captured.getValue().getPayload());
        assertThat(payload.schemaVersion()).isEqualTo(RunPodContract.REQUEST_V4);
        assertThat(payload.resultSchemaVersion()).isEqualTo(RunPodContract.RESULT_V6);
        assertThat(payload.analysisProfile()).isEqualTo(RunPodContract.AUDIOVISUAL_PROFILE);
        assertThat(payload.video()).isNull();
        assertThat(payload.mediaPreparation()).isNull();
        assertThat(result.getExpectedResultSchemaVersion()).isEqualTo(RunPodContract.RESULT_V6);
        verifyNoInteractions(video);
        verify(settings).reserveAdmissionBudget(CanonicalHandoffSettings.AUDIOVISUAL_RESERVATION);
        var event=request.eventId();
        verify(registry).registerCurrent(eq(1L),eq(event),any(UUID.class));
    }

    @Test void refusesRetiredWriterAndDoesNotRewriteAnOldOutbox() {
        assertThatThrownBy(()->publisher.publish(request,AnalysisExecutionProfile.CANONICAL_HANDOFF))
                .isInstanceOf(BaseException.class);
        verifyNoInteractions(outboxes,registry);
    }

    @Test void videoNeedsItsOwnReadinessBeforeAdmissionOrAnyOutboxWrite() {
        when(request.visualInput()).thenReturn(mock(org.example.voice.analysis.domain.model.AnalysisWorkerVisualInput.class));
        assertThatThrownBy(()->publisher.publish(request)).isInstanceOf(BaseException.class);
        verify(video).admission();
        verifyNoInteractions(outboxes,registry,settings);
    }
}
