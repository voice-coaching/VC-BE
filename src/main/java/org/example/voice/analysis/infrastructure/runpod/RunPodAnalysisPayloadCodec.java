package org.example.voice.analysis.infrastructure.runpod;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RunPodAnalysisPayloadCodec {
    private final RunPodContract contract = new RunPodContract();

    private final ObjectMapper objectMapper;

    public RunPodAnalysisPayloadCodec() {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.objectMapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    String encodeRequest(RunPodAnalysisJobRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            contract.parse(json.getBytes(StandardCharsets.UTF_8), "analysisRequest");
            return json;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("runpod analysis request serialization failed", error);
        }
    }

    RunPodAnalysisJobRequest decodeRequest(String payload) {
        if (payload == null) throw new RunPodContractException(422, "VALIDATION_FAILED");
        var node = contract.parse(payload.getBytes(StandardCharsets.UTF_8), "analysisRequest");
        try {
            RunPodAnalysisJobRequest request = objectMapper.readerFor(RunPodAnalysisJobRequest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(node);
            // Never silently change the immutable request identity while mapping its typed fields.
            if (!contract.digest(payload).equals(contract.digest(encodeRequest(request)))) {
                throw new RunPodContractException(422, "VALIDATION_FAILED");
            }
            return request;
        } catch (IOException error) {
            throw new RunPodContractException(422, "VALIDATION_FAILED");
        }
    }

    int payloadBytes(String payload) {
        return payload == null ? 0 : payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
