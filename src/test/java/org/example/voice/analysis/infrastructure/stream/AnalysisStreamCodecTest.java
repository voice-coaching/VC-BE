package org.example.voice.analysis.infrastructure.stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisStreamCodecTest {

    private final AnalysisStreamCodec codec = new AnalysisStreamCodec();

    @Test
    void decodesV4SeungunEvidenceAndClosedBetaRawMedia() {
        var result = codec.decodeResult("""
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
                """);

        assertThat(result.closedBetaDebug().context().userId()).isEqualTo(9L);
        assertThat(result.closedBetaDebug().audioMediaBase64()).isEqualTo("cmF3");
        assertThat(result.seungunProductionEvidence().get("schema_version"))
                .isEqualTo("korean_phone_ctc.production_analysis.v2");
    }

    @Test
    void decodesClosedBetaBase64AboveJacksonsHistoricalDefaultStringLimit() {
        String rawMedia = "A".repeat(20_000_004);
        var result = codec.decodeResult("""
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
}
