package org.example.voice.analysis.infrastructure.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;
import org.springframework.stereotype.Component;

/** Strict JSON codec for the single `payload` field stored in each stream entry. */
@Component
public class AnalysisStreamCodec {

    private static final int CLOSED_BETA_MAXIMUM_JSON_STRING_LENGTH = 180_000_000;

    private final ObjectMapper objectMapper;

    public AnalysisStreamCodec() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder()
                        .maxStringLength(CLOSED_BETA_MAXIMUM_JSON_STRING_LENGTH)
                        .build()
        );
    }

    public String encodeRequest(AnalysisWorkerRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("analysis request serialization failed", error);
        }
    }

    public AnalysisWorkerResult decodeResult(String payload) {
        try {
            return objectMapper.readerFor(AnalysisWorkerResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("analysis result payload is invalid", error);
        }
    }
}
