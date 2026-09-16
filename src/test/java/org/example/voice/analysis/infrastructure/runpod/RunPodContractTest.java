package org.example.voice.analysis.infrastructure.runpod;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

public class RunPodContractTest {
    public static final UUID REQUEST = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final UUID EXECUTION = UUID.fromString("22222222-2222-4222-8222-222222222222");
    public static final UUID WORKER = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private final RunPodContract contract = new RunPodContract();

    public static String payload(OffsetDateTime deadline) {
        return """
                {"schemaVersion":"voice-coaching.runpod-analysis-request.v1",
                 "requestId":"%s","executionId":"%s","analysisId":1,"recordingId":2,"contentId":3,
                 "learningFocus":"PRONUNCIATION","promptRevision":"v1","scriptText":"안녕하세요",
                 "scriptSha256":"%s","audio":{"objectKey":"recordings/analysis-audio/test.wav",
                 "mimeType":"audio/wav","sha256":"%s","fileSizeBytes":100,"durationMs":1000},
                 "video":null,"deadlineAt":"%s"}
                """.formatted(REQUEST, EXECUTION, "a".repeat(64), "b".repeat(64), RunPodContract.timestamp(deadline));
    }

    public static String result(UUID event) {
        return """
                {"schemaVersion":"voice-coaching.runpod-analysis-result.v1",
                 "eventId":"%s","requestId":"%s","executionId":"%s","workerInstanceId":"%s",
                 "analysisId":1,"recordingId":2,"status":"COMPLETED","outcome":"COMPLETED_NO_ISSUE",
                 "summaryFeedback":null,"pronunciationEvidence":null,"visualSupplement":null,
                 "audioSha256":"%s","workerRevision":"worker1","pipelineRevision":"pipeline1",
                 "failureCode":null,"failureReason":null}
                """.formatted(event, REQUEST, EXECUTION, WORKER, "b".repeat(64));
    }

    @Test
    void serializesExplicitNullAndUtcMillisecondsAndRoundTrips() throws Exception {
        var codec = new RunPodAnalysisPayloadCodec();
        var request = codec.decodeRequest(payload(OffsetDateTime.parse("2026-09-16T10:00:00.000Z")));
        String wire = codec.encodeRequest(request);
        assertThat(wire).contains("\"video\":null", "\"deadlineAt\":\"2026-09-16T10:00:00.000Z\"");
        assertThat(contract.digest(wire)).isEqualTo(contract.digest(payload(OffsetDateTime.parse("2026-09-16T10:00:00.000Z"))));
        contract.parse(result(UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "result");
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/runpod-contract-fixtures"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/runpod-contract-fixtures/analysisRequest.json"), wire);
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/runpod-contract-fixtures/request.sha256"), contract.digest(wire));
    }

    @Test
    void validatesStrictBoundaryAndLegacyPayloadRejection() {
        String good = payload(OffsetDateTime.now().plusMinutes(5));
        reject(good.replace("\"video\":null,", ""), "analysisRequest", 422);
        reject(good.replace("\"video\":null", "\"video\":null,\"debug\":true"), "analysisRequest", 422);
        reject(good.replace("\"analysisId\":1", "\"analysisId\":9007199254740992"), "analysisRequest", 422);
        reject(good.replace("\"analysisId\":1", "\"analysisId\":1,\"analysisId\":2"), "analysisRequest", 400);
        reject(good + " {}", "analysisRequest", 400);
        reject(payload(OffsetDateTime.parse("2026-09-16T10:00:00Z")).replace("2026-09-16", "2026-02-30"), "analysisRequest", 422);
        reject("{\"requestId\":\""+REQUEST+"\",\"executionId\":\""+EXECUTION+"\",\"workerInstanceId\":\""+WORKER+"\",\"heartbeatAt\":\"2026-09-16T10:00:00.000Z\"}", "heartbeatRequest", 422);
        reject(result(UUID.randomUUID()).replace("\"summaryFeedback\":null", "\"summaryFeedback\":null,\"transcript\":\"unsupported\""), "result", 422);
        assertThatThrownBy(() -> contract.parse(new byte[RunPodContract.CONTROL_LIMIT+1], "claimRequest"))
                .isInstanceOfSatisfying(RunPodContractException.class, e -> assertThat(e.status()).isEqualTo(413));
    }

    @Test
    void canonicalDigestIgnoresObjectOrderButNotContent() {
        assertThat(contract.digest("{\"b\":2,\"a\":1.0}")).isEqualTo(contract.digest("{\"a\":1,\"b\":2}"));
        assertThat(contract.digest("{\"a\":1}")).isNotEqualTo(contract.digest("{\"a\":2}"));
    }

    private void reject(String json, String kind, int status) {
        assertThatThrownBy(() -> contract.parse(json.getBytes(StandardCharsets.UTF_8), kind))
                .isInstanceOfSatisfying(RunPodContractException.class, e -> assertThat(e.status()).isEqualTo(status));
    }
}
