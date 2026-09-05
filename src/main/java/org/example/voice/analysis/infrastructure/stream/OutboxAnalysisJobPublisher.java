package org.example.voice.analysis.infrastructure.stream;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Writes the event inside the request transaction; a separate dispatcher performs Redis I/O. */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class OutboxAnalysisJobPublisher implements AnalysisJobPublisher {

    private final AnalysisRequestOutboxJpaRepository outboxRepository;
    private final AnalysisResultJpaRepository analysisResultRepository;
    private final AnalysisStreamCodec codec;
    private final AnalysisStreamProperties properties;

    @Override
    @Transactional
    public void publish(AnalysisWorkerRequest request) {
        AnalysisResult analysisResult = analysisResultRepository.findById(request.analysisId())
                .orElseThrow(() -> new IllegalStateException("analysis result disappeared before outbox write"));
        if (!analysisResult.isForActiveRequest(request.eventId())) {
            throw new IllegalStateException("analysis request event does not match active analysis request");
        }
        String payload = codec.encodeRequest(request);
        if (RedisAnalysisResultConsumer.payloadBytes(payload) > properties.getMaximumPayloadBytes()) {
            throw new IllegalStateException("analysis_request_payload_size_invalid");
        }
        outboxRepository.save(AnalysisRequestOutbox.pending(
                request.eventId(),
                analysisResult,
                payload
        ));
    }
}
