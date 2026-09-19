package org.example.voice.analysis.domain.entity;

import org.example.voice.analysis.domain.model.AnalysisWorkerPronunciationEvidence;
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerVisualSupplement;
import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisResultPronunciationEvidenceTest {

    @Test
    void storesAndClearsTheSameAttemptPronunciationEvidenceAsOneUnit() {
        UUID requestEventId = UUID.randomUUID();
        AnalysisResult entity = AnalysisResult.pending(registeredRecording(), requestEventId);

        assertThat(entity.complete(completed(requestEventId))).isTrue();
        assertThat(entity.getAnalysisOutcome()).isEqualTo(AnalysisOutcome.COACHING_READY);
        assertThat(entity.getSelectedPhone()).isEqualTo("ㄱ");
        assertThat(entity.getSelectedExpectedIndex()).isZero();
        assertThat(entity.getDetectorScore()).isEqualByComparingTo("0.91");
        assertThat(entity.getScoreSemantics())
                .isEqualTo(AnalysisWorkerPronunciationEvidence.SCORE_SEMANTICS);

        entity.retry(UUID.randomUUID());

        assertThat(entity.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(entity.getSelectedPhone()).isNull();
        assertThat(entity.getDetectorScore()).isNull();
        assertThat(entity.getPronunciationEvidenceSchemaVersion()).isNull();
    }

    @Test
    void sessionCancellationDiscardsAnAlreadyArrivedResultAndWinsLateDelivery() {
        UUID requestEventId = UUID.randomUUID();
        AnalysisResult entity = AnalysisResult.pending(registeredRecording(), requestEventId);
        assertThat(entity.complete(completed(requestEventId))).isTrue();

        assertThat(entity.cancel("analysis_session_canceled", "canceled")).isTrue();

        assertThat(entity.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(entity.getFailureCode()).isEqualTo("analysis_session_canceled");
        assertThat(entity.getSelectedPhone()).isNull();
        assertThat(entity.getSummaryFeedback()).isNull();
        assertThat(entity.complete(completed(requestEventId))).isFalse();
    }

    @Test
    void storesAndClearsClosedBetaAggregateLipObservation() {
        UUID requestEventId = UUID.randomUUID();
        VoiceRecording recording = registeredRecording();
        when(recording.getVisualObjectKey()).thenReturn("recordings/canonical-video.mp4");
        when(recording.getVisualSha256()).thenReturn("b".repeat(64));
        AnalysisResult entity = AnalysisResult.pending(recording, requestEventId);
        AnalysisWorkerResult result = completedWithVisual(requestEventId);

        assertThat(entity.complete(result)).isTrue();
        assertThat(entity.getVisualClosedBetaLipObservation()).isEqualTo(lipObservation());

        entity.retry(UUID.randomUUID());
        assertThat(entity.getVisualClosedBetaLipObservation()).isNull();
    }

    @Test
    void rejectsAnotherRecordingsAudioBeforeStoringAnyResult() {
        UUID requestEventId = UUID.randomUUID();
        VoiceRecording recording = registeredRecording();
        when(recording.getAudioSha256()).thenReturn("b".repeat(64));
        AnalysisResult entity = AnalysisResult.pending(recording, requestEventId);

        assertThatThrownBy(() -> entity.complete(completed(requestEventId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("analysis result does not bind the registered audio");
        assertThat(entity.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(entity.getSelectedPhone()).isNull();
        assertThat(entity.getSummaryFeedback()).isNull();
        assertThat(entity.getAudioSha256()).isNull();
    }

    @Test
    void rejectsVisualEvidenceWithoutARegisteredVideoBeforeStoringAnyResult() {
        UUID requestEventId = UUID.randomUUID();
        AnalysisResult entity = AnalysisResult.pending(registeredRecording(), requestEventId);

        assertThatThrownBy(() -> entity.complete(completedWithVisual(requestEventId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("visual result requires the registered same-attempt video");
        assertThat(entity.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(entity.getSelectedPhone()).isNull();
        assertThat(entity.getSummaryFeedback()).isNull();
        assertThat(entity.getVisualClosedBetaLipObservation()).isNull();
    }

    private VoiceRecording registeredRecording() {
        VoiceRecording recording = mock(VoiceRecording.class);
        when(recording.getAudioSha256()).thenReturn("a".repeat(64));
        return recording;
    }

    private Map<String, Object> lipObservation() {
        return Map.of(
                "schemaVersion", "voice-coaching.closed-beta-lip-observation.v1",
                "status", "OBSERVED",
                "selectedExpectedIndex", 0,
                "videoStartMs", 100L,
                "videoEndMs", 200L,
                "geometryArtifactSha256", "2".repeat(64),
                "measurements", Map.of(
                        "inner_aperture_ratio", Map.of("value", 0.25, "unit", "ratio")
                ),
                "containsPronunciationTruth", false,
                "containsActionTruth", false
        );
    }

    private AnalysisWorkerResult completedWithVisual(UUID requestEventId) {
        AnalysisWorkerResult base = completed(requestEventId);
        return new AnalysisWorkerResult(
                base.schemaVersion(), base.eventId(), base.requestEventId(), base.analysisId(),
                base.status(), base.outcome(), base.failureCode(), base.failureReason(),
                base.transcript(), base.sttConfidence(), base.sttModelName(), base.overallScore(),
                base.pronunciationScore(), base.intonationScore(), base.speedWpm(), base.speedStatus(),
                base.stressScore(), base.pauseScore(), base.strengthsText(), base.weaknessesText(),
                base.summaryFeedback(), base.pronunciationEvidence(), base.workerRevision(),
                base.pipelineRevision(), base.audioSha256(), base.segments(),
                new AnalysisWorkerVisualSupplement(
                        AnalysisWorkerVisualSupplement.SCHEMA_VERSION,
                        0,
                        "supports_upstream",
                        "lip.aperture.low",
                        "lip_aperture_hint",
                        "f".repeat(64),
                        "1".repeat(64),
                        lipObservation()
                )
        );
    }

    private AnalysisWorkerResult completed(UUID requestEventId) {
        return new AnalysisWorkerResult(
                AnalysisWorkerResult.SCHEMA_VERSION,
                UUID.randomUUID(),
                requestEventId,
                1L,
                AnalysisStatus.COMPLETED,
                AnalysisOutcome.COACHING_READY,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "승인된 같은-attempt 피드백",
                new AnalysisWorkerPronunciationEvidence(
                        AnalysisWorkerPronunciationEvidence.SCHEMA_VERSION,
                        "ㄱ",
                        0,
                        100,
                        200,
                        new BigDecimal("0.91"),
                        new BigDecimal("0.80"),
                        AnalysisWorkerPronunciationEvidence.SCORE_SEMANTICS,
                        AnalysisWorkerPronunciationEvidence.EVIDENCE_STATE
                ),
                "worker-v1",
                "seungun-v1",
                "a".repeat(64),
                List.of()
        );
    }
}
