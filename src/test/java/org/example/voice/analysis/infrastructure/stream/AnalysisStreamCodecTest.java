package org.example.voice.analysis.infrastructure.stream;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationGrant;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisStreamCodecTest {

    private final AnalysisStreamCodec codec = new AnalysisStreamCodec();

    @Test
    void decodesV4SeungunEvidenceAndClosedBetaRawMedia() {
        String payload = """
                {
                  "schemaVersion": "voice-coaching.analysis-result.v4",
                  "eventId": "e917fda8-3c4f-4b7e-9094-7a1706081f1b",
                  "requestEventId": "4adfe173-0691-4e89-b94e-a5c5c5085826",
                  "analysisId": 35,
                  "status": "COMPLETED",
                  "outcome": "COMPLETED_NO_ISSUE",
                  "workerRevision": "vc-be-redis-worker-v2",
                  "pipelineRevision": "g2pk:2.0.0|seungun:production-v2",
                  "audioSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "segments": [],
                  "seungunProductionEvidence": {
                    "schema_version": "korean_phone_ctc.production_analysis.v2"
                  },
                  "closedBetaDebug": {
                    "schemaVersion": "voice-coaching.closed-beta-debug.v1",
                    "context": {
                      "schemaVersion": "voice-coaching.closed-beta-context.v1",
                      "userId": 9,
                      "sessionId": 7,
                      "recordingId": 50
                    },
                    "captureState": "COMPLETE",
                    "audioObjectKey": "recordings/50.wav",
                    "visualObjectKey": null,
                    "materializedAudioPath": "/restricted/source.wav",
                    "decodedAudioPath": "/restricted/decoded.wav",
                    "materializedVideoPath": null,
                    "audioMediaBase64": "cmF3",
                    "decodedAudioMediaBase64": "ZGVjb2RlZA==",
                    "videoMediaBase64": null
                  }
                }
                """;
        var result = closedBetaCodec().decodeResult(payload);

        assertThat(result.closedBetaDebug().context().userId()).isEqualTo(9L);
        assertThat(result.closedBetaDebug().audioMediaBase64()).isEqualTo("cmF3");
        assertThat(result.seungunProductionEvidence().get("schema_version"))
                .isEqualTo("korean_phone_ctc.production_analysis.v2");
        assertThatThrownBy(() -> codec.decodeResult(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("closed beta analysis result is disabled");
    }

    @Test
    void decodesClosedBetaBase64AboveJacksonsHistoricalDefaultStringLimit() {
        String rawMedia = "A".repeat(20_000_004);
        var result = closedBetaCodec().decodeResult("""
                {
                  "schemaVersion": "voice-coaching.analysis-result.v4",
                  "eventId": "e917fda8-3c4f-4b7e-9094-7a1706081f1b",
                  "requestEventId": "4adfe173-0691-4e89-b94e-a5c5c5085826",
                  "analysisId": 35,
                  "status": "FAILED",
                  "failureCode": "closed_beta_test",
                  "failureReason": "closed beta test",
                  "segments": [],
                  "closedBetaDebug": {
                    "schemaVersion": "voice-coaching.closed-beta-debug.v1",
                    "context": {
                      "schemaVersion": "voice-coaching.closed-beta-context.v1",
                      "userId": 9,
                      "sessionId": 7,
                      "recordingId": 50
                    },
                    "captureState": "PARTIAL",
                    "audioObjectKey": "recordings/50.wav",
                    "visualObjectKey": null,
                    "materializedAudioPath": "/restricted/source.wav",
                    "decodedAudioPath": null,
                    "materializedVideoPath": null,
                    "audioMediaBase64": "%s",
                    "decodedAudioMediaBase64": null,
                    "videoMediaBase64": null
                  }
                }
                """.formatted(rawMedia));

        assertThat(result.closedBetaDebug().audioMediaBase64()).hasSize(20_000_004);
    }

    @Test
    void decodesGroundedResultV2WithSameAttemptPronunciationEvidence() {
        var result = codec.decodeResult("""
                {
                  "schemaVersion": "voice-coaching.analysis-result.v3",
                  "eventId": "e917fda8-3c4f-4b7e-9094-7a1706081f1b",
                  "requestEventId": "4adfe173-0691-4e89-b94e-a5c5c5085826",
                  "analysisId": 35,
                  "status": "COMPLETED",
                  "outcome": "COACHING_READY",
                  "summaryFeedback": "목표 음소 ‘ㄱ’ 소리를 천천히 분리해 발음해 보세요.",
                  "pronunciationEvidence": {
                    "schemaVersion": "voice-coaching.pronunciation-evidence.v1",
                    "selectedPhone": "ㄱ",
                    "selectedExpectedIndex": 0,
                    "selectedStartMs": 120,
                    "selectedEndMs": 240,
                    "detectorScore": 0.91,
                    "operatingThreshold": 0.8,
                    "scoreSemantics": "detector_ranking_score_not_calibrated_correctness_confidence",
                    "evidenceState": "frozen_detector_threshold_passed"
                  },
                  "workerRevision": "vc-be-redis-worker-v1",
                  "pipelineRevision": "g2pk:2.0.0|seungun:frozen-v1",
                  "audioSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "segments": []
                }
                """);

        assertThat(result.pronunciationEvidence().selectedPhone()).isEqualTo("ㄱ");
        assertThat(result.pronunciationEvidence().selectedExpectedIndex()).isZero();
    }

    @Test
    void rejectsLegacyResultV1() {
        assertThatThrownBy(() -> codec.decodeResult("""
                {
                  "schemaVersion": "voice-coaching.analysis-result.v1",
                  "eventId": "e917fda8-3c4f-4b7e-9094-7a1706081f1b",
                  "requestEventId": "4adfe173-0691-4e89-b94e-a5c5c5085826",
                  "analysisId": 35,
                  "status": "COMPLETED",
                  "outcome": "COMPLETED_NO_ISSUE",
                  "workerRevision": "legacy-worker",
                  "pipelineRevision": "legacy-pipeline",
                  "audioSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "segments": []
                }
                """))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownPronunciationEvidenceFields() {
        assertThatThrownBy(() -> codec.decodeResult("""
                {
                  "schemaVersion": "voice-coaching.analysis-result.v3",
                  "eventId": "e917fda8-3c4f-4b7e-9094-7a1706081f1b",
                  "requestEventId": "4adfe173-0691-4e89-b94e-a5c5c5085826",
                  "analysisId": 35,
                  "status": "COMPLETED",
                  "outcome": "COACHING_READY",
                  "summaryFeedback": "승인된 피드백",
                  "pronunciationEvidence": {
                    "schemaVersion": "voice-coaching.pronunciation-evidence.v1",
                    "selectedPhone": "ㄱ",
                    "selectedExpectedIndex": 0,
                    "selectedStartMs": null,
                    "selectedEndMs": null,
                    "detectorScore": 0.91,
                    "operatingThreshold": 0.8,
                    "scoreSemantics": "detector_ranking_score_not_calibrated_correctness_confidence",
                    "evidenceState": "frozen_detector_threshold_passed",
                    "diagnosis": "unapproved"
                  },
                  "workerRevision": "worker",
                  "pipelineRevision": "pipeline",
                  "audioSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "segments": []
                }
                """))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializesProductionRequestWithExactFieldsAndUtcStrings() throws Exception {
        String script = "test pronunciation";
        String objectKey = "recordings/analysis-audio/00000000-0000-0000-0000-000000000001.wav";
        UUID requestEventId = UUID.fromString("4adfe173-0691-4e89-b94e-a5c5c5085826");
        Instant issuedAt = Instant.parse("2026-09-02T00:00:00.123456789Z");
        String scriptSha256 = sha256(script);
        AnalysisAuthorizationGrant grant = new AnalysisAuthorizationGrant(
                AnalysisAuthorizationGrant.LEGACY_GRANT_VERSION,
                "test-key-v1",
                requestEventId,
                35L,
                12L,
                "prompt-v1",
                scriptSha256,
                sha256(objectKey),
                "d".repeat(64),
                "audio/wav",
                1234L,
                1200,
                LearningFocus.PRONUNCIATION,
                "e".repeat(64),
                "voice-analysis-consent-v1",
                issuedAt,
                issuedAt.plusSeconds(300),
                AnalysisAuthorizationGrant.PURPOSE,
                AnalysisAuthorizationGrant.DATA_CATEGORY,
                true,
                false,
                "c".repeat(64)
        );
        AnalysisWorkerRequest request = new AnalysisWorkerRequest(
                AnalysisWorkerRequest.LEGACY_SCHEMA_VERSION,
                requestEventId,
                35L,
                12L,
                "prompt-v1",
                script,
                scriptSha256,
                objectKey,
                "d".repeat(64),
                "audio/wav",
                1234L,
                1200,
                LearningFocus.PRONUNCIATION,
                grant
        );

        JsonNode payload = new ObjectMapper().readTree(codec.encodeRequest(request));

        assertThat(payload.size()).isEqualTo(15);
        assertThat(payload.path("schemaVersion").asText()).isEqualTo("voice-coaching.analysis-request.v4");
        assertThat(payload.has("closedBetaContext")).isFalse();
        assertThat(payload.has("visualInput")).isTrue();
        assertThat(payload.path("visualInput").isNull()).isTrue();
        JsonNode authorization = payload.path("authorizationGrant");
        assertThat(authorization.size()).isEqualTo(28);
        assertThat(authorization.path("grantVersion").asText())
                .isEqualTo("voice-coaching.analysis-authorization.v3");
        assertThat(authorization.has("closedBetaContextSha256")).isFalse();
        for (String field : new String[]{"visualObjectKeySha256", "visualSha256", "visualMimeType",
                "visualFileSizeBytes", "visualConsentReceiptSha256", "visualConsentPolicyRevision"}) {
            assertThat(authorization.has(field)).as(field + " must be present").isTrue();
            assertThat(authorization.path(field).isNull()).as(field + " must be null").isTrue();
        }
        assertThat(authorization.path("issuedAtUtc").isTextual()).isTrue();
        assertThat(authorization.path("expiresAtUtc").isTextual()).isTrue();
        assertThat(authorization.path("issuedAtUtc").asText()).isEqualTo(issuedAt.toString());
        assertThat(authorization.path("expiresAtUtc").asText()).isEqualTo(issuedAt.plusSeconds(300).toString());
    }

    @Test
    void rejectsDuplicateKeysBeforeBindingProductionResult() {
        assertThatThrownBy(() -> codec.decodeResult("""
                {
                  "schemaVersion": "voice-coaching.analysis-result.v3",
                  "eventId": "e917fda8-3c4f-4b7e-9094-7a1706081f1b",
                  "requestEventId": "4adfe173-0691-4e89-b94e-a5c5c5085826",
                  "analysisId": 35,
                  "analysisId": 36,
                  "status": "PROCESSING",
                  "segments": []
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("analysis result payload is invalid")
                .hasCauseInstanceOf(JsonParseException.class);
    }

    @Test
    void rejectsProductionPayloadOverUtf8ByteLimit() {
        String payload = "{\"summaryFeedback\":\"" + "\uac00".repeat(350_000) + "\"}";

        assertThat(payload.length()).isLessThan(1_048_576);
        assertThatThrownBy(() -> codec.decodeResult(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("analysis result payload exceeds configured limit");
    }

    @Test
    void requiresExplicitClosedBetaModeToIncreaseResultLimit() {
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        properties.setMaximumResultPayloadBytes(384 * 1024 * 1024);

        assertThatThrownBy(() -> new AnalysisStreamCodec(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("analysis_stream_resource_limits_invalid");
    }

    private static AnalysisStreamCodec closedBetaCodec() {
        AnalysisStreamProperties properties = new AnalysisStreamProperties();
        properties.setClosedBetaEnabled(true);
        properties.setMaximumResultPayloadBytes(384 * 1024 * 1024);
        return new AnalysisStreamCodec(properties);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
