package org.example.voice.analysis.infrastructure.runpod;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

@Component
public class RunPodAnalysisPayloadCodec {

    private final ObjectMapper objectMapper;

    public RunPodAnalysisPayloadCodec() {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.objectMapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    String encodeRequest(RunPodAnalysisJobRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("runpod analysis request serialization failed", error);
        }
    }

    RunPodAnalysisJobRequest decodeRequest(String payload) {
        try {
            return objectMapper.readerFor(RunPodAnalysisJobRequest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("runpod analysis request payload is invalid", error);
        }
    }

    int payloadBytes(String payload) {
        return payload == null ? 0 : payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
