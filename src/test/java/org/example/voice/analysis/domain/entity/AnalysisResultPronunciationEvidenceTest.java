package org.example.voice.analysis.domain.entity;

import org.example.voice.analysis.domain.model.AnalysisWorkerPronunciationEvidence;
import org.example.voice.analysis.domain.model.AnalysisWorkerResult;
import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalysisResultPronunciationEvidenceTest {

    @Test
    void storesAndClearsTheSameAttemptPronunciationEvidenceAsOneUnit() {
        UUID requestEventId = UUID.randomUUID();
        AnalysisResult entity = AnalysisResult.pending(mock(VoiceRecording.class), requestEventId);

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
