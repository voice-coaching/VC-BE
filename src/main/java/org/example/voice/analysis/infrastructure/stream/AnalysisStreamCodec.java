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
        if (maximumResultPayloadBytes < 1_024 || maximumResultPayloadBytes > modeMaximum) {
            throw new IllegalStateException("analysis_stream_resource_limits_invalid");
        }
        objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        objectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        objectMapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder()
                        .maxStringLength(closedBetaEnabled
                                ? CLOSED_BETA_MAXIMUM_JSON_STRING_LENGTH
                                : PRODUCTION_MAXIMUM_JSON_STRING_LENGTH)
                        .build()
        );
    }

    public String encodeRequest(AnalysisWorkerRequest request) {
        String expectedSchema = closedBetaEnabled
                ? AnalysisWorkerRequest.SCHEMA_VERSION
                : AnalysisWorkerRequest.LEGACY_SCHEMA_VERSION;
        if (request == null || !expectedSchema.equals(request.schemaVersion())) {
            throw new IllegalArgumentException("analysis request contract does not match configured mode");
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("analysis request serialization failed", error);
        }
    }

    public AnalysisWorkerResult decodeResult(String payload) {
        if (payload == null
                || payload.length() > maximumResultPayloadBytes
                || payload.getBytes(StandardCharsets.UTF_8).length > maximumResultPayloadBytes) {
            throw new IllegalArgumentException("analysis result payload exceeds configured limit");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("analysis result payload must be an object");
            }
            if (!closedBetaEnabled
                    && (!AnalysisWorkerResult.LEGACY_SCHEMA_VERSION.equals(root.path("schemaVersion").asText())
                    || root.hasNonNull("closedBetaDebug")
                    || root.hasNonNull("seungunProductionEvidence"))) {
                throw new IllegalArgumentException("closed beta analysis result is disabled");
            }
            return objectMapper.readerFor(AnalysisWorkerResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(root);
        } catch (IOException error) {
            throw new IllegalArgumentException("analysis result payload is invalid", error);
        }
    }
}
