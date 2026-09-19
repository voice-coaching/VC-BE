package org.example.voice.analysis.infrastructure.stream;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxAnalysisJobPublisherTest {

    @Test
    void rejectsOversizedRequestBeforePersistingTheOutbox() {
        UUID eventId = UUID.randomUUID();
        AnalysisWorkerRequest request = mock(AnalysisWorkerRequest.class);
        when(request.analysisId()).thenReturn(35L);
        when(request.eventId()).thenReturn(eventId);
        AnalysisResult result = mock(AnalysisResult.class);
        when(result.isForActiveRequest(eventId)).thenReturn(true);
        AnalysisResultJpaRepository results = mock(AnalysisResultJpaRepository.class);
        when(results.findById(35L)).thenReturn(Optional.of(result));
        AnalysisStreamCodec codec = mock(AnalysisStreamCodec.class);
        when(codec.encodeRequest(request)).thenReturn("x".repeat(129));
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        properties.setMaximumPayloadBytes(128);
        AnalysisRequestOutboxJpaRepository outbox = mock(AnalysisRequestOutboxJpaRepository.class);

        assertThatThrownBy(() -> new OutboxAnalysisJobPublisher(
                outbox, results, codec, properties
        ).publish(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("analysis_request_payload_size_invalid");

        verify(outbox, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
