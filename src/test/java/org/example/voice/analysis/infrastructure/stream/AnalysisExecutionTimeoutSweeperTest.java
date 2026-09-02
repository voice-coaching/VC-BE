package org.example.voice.analysis.infrastructure.stream;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisRequestOutboxStatus;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.training.domain.entity.TrainingSession;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisExecutionTimeoutSweeperTest {

    @Test
    void failsStaleGenerationCancelsPendingDispatchAndRevokesConsent() {
        TrainingSession session = mock(TrainingSession.class);
        when(session.getId()).thenReturn(7L);
        when(session.getUserId()).thenReturn(9L);
        VoiceRecording recording = mock(VoiceRecording.class);
        when(recording.getTrainingSession()).thenReturn(session);
        AnalysisResult result = AnalysisResult.pending(recording, UUID.randomUUID());

        AnalysisResultJpaRepository results = mock(AnalysisResultJpaRepository.class);
        when(results.findStaleForUpdate(
                eq(List.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING)),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(result));
        AnalysisRequestOutboxJpaRepository outbox = mock(AnalysisRequestOutboxJpaRepository.class);
        when(outbox.findByAnalysisResultIdAndStatus(null, AnalysisRequestOutboxStatus.PENDING))
                .thenReturn(List.of());
        ProcessingConsentLedger consent = mock(ProcessingConsentLedger.class);

        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        new AnalysisExecutionTimeoutSweeper(results, outbox, consent, properties).failStaleAnalyses();

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getFailureCode()).isEqualTo(AnalysisExecutionTimeoutSweeper.TIMEOUT_CODE);
        verify(consent).revokeForSession(9L, 7L);
    }
}
