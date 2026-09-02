package org.example.voice.training.application;

import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.analysis.domain.port.AnalysisCancellation;
import org.example.voice.training.domain.model.TrainingSessionCancellationData;
import org.example.voice.training.domain.port.TrainingAnalysisReader;
import org.example.voice.training.domain.port.TrainingSessionReader;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.port.RecordingUploadIntentRegistry;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingSessionServiceTest {

    @Test
    void cancelRevokesEveryProcessingConsentForTheOwnedSession() {
        TrainingSessionReader reader = mock(TrainingSessionReader.class);
        TrainingSessionWriter writer = mock(TrainingSessionWriter.class);
        ProcessingConsentLedger consentLedger = mock(ProcessingConsentLedger.class);
        RecordingUploadIntentRegistry uploadIntentRegistry = mock(RecordingUploadIntentRegistry.class);
        AnalysisCancellation analysisCancellation = mock(AnalysisCancellation.class);
        TrainingSessionService service = new TrainingSessionService(
                reader,
                writer,
                mock(TrainingAnalysisReader.class),
                consentLedger,
                uploadIntentRegistry,
                analysisCancellation
        );
        OffsetDateTime canceledAt = OffsetDateTime.now();
        when(reader.findSessionStatus(7L, 9L)).thenReturn(Optional.of(TrainingSessionStatus.ANALYZING));
        when(writer.cancel(7L)).thenReturn(
                new TrainingSessionCancellationData(7L, TrainingSessionStatus.CANCELED, canceledAt)
        );

        var result = service.cancel(7L, 9L);

        assertThat(result.canceledAt()).isEqualTo(canceledAt);
        verify(consentLedger).revokeForSession(9L, 7L);
        verify(uploadIntentRegistry).expireForSession(9L, 7L);
        verify(analysisCancellation).cancelForSession(7L);
    }
}
