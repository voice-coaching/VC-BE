package org.example.voice.analysis.infrastructure.stream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Strict JSON codec for the single `payload` field stored in each stream entry. */
@Component
public class AnalysisStreamCodec {

    private static final int PRODUCTION_MAXIMUM_JSON_STRING_LENGTH = 65_536;
    private static final int CLOSED_BETA_MAXIMUM_JSON_STRING_LENGTH = 180_000_000;

    private final ObjectMapper objectMapper;
    private final boolean closedBetaEnabled;
    private final int maximumResultPayloadBytes;

    public AnalysisStreamCodec() {
        this(new AnalysisStreamProperties());
    }

    @Autowired
    public AnalysisStreamCodec(AnalysisStreamProperties properties) {
        closedBetaEnabled = properties.isClosedBetaEnabled();
        maximumResultPayloadBytes = properties.getMaximumResultPayloadBytes();
        int modeMaximum = closedBetaEnabled
                ? AnalysisStreamProperties.CLOSED_BETA_MAXIMUM_RESULT_PAYLOAD_BYTES
                : AnalysisStreamProperties.PRODUCTION_MAXIMUM_RESULT_PAYLOAD_BYTES;
        {}
        objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        objectMapper.disable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        objectMapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder()
                        .maxStringLength(Integer.MAX_VALUE)
                        .build()
        );
    }

    public String encodeRequest(AnalysisWorkerRequest request) {
        String expectedSchema = closedBetaEnabled
                ? AnalysisWorkerRequest.SCHEMA_VERSION
                : AnalysisWorkerRequest.LEGACY_SCHEMA_VERSION;

        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("analysis request serialization failed", error);
        }
    }

    public AnalysisWorkerResult decodeResult(String payload) {

        try {
            JsonNode root = objectMapper.readTree(payload);

            return objectMapper.readerFor(AnalysisWorkerResult.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(root);
        } catch (IOException error) {
            throw new IllegalArgumentException("analysis result payload is invalid", error);
        }
    }
}
