package org.example.voice.analysis.domain.model;

import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisWorkerPayloadTest {

    @Test
    void rejectsPresignedUrlWhereOnlyAnObjectKeyIsAllowed() {
        assertThatThrownBy(() -> new AnalysisWorkerRequest(
                AnalysisWorkerRequest.SCHEMA_VERSION,
                UUID.randomUUID(),
                1L,
                2L,
                "2026-09-02T00:00:00Z",
                "연습 문장입니다.",
                "a".repeat(64),
                "https://storage.example.com/recordings/1.wav?signature=secret",
                "audio/wav",
                1L,
                1,
                LearningFocus.PRONUNCIATION,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTraversingAudioObjectKey() {
        assertThatThrownBy(() -> new AnalysisWorkerRequest(
                AnalysisWorkerRequest.SCHEMA_VERSION,
                UUID.randomUUID(),
                1L,
                2L,
                "2026-09-02T00:00:00Z",
                "연습 문장입니다.",
                "a".repeat(64),
                "recordings/../private.wav",
                "audio/wav",
                1L,
                1,
                LearningFocus.PRONUNCIATION,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAmbiguousObjectKeyAndBlankMimeType() {
        assertThatThrownBy(() -> new AnalysisWorkerRequest(
                AnalysisWorkerRequest.SCHEMA_VERSION,
                UUID.randomUUID(),
                1L,
                2L,
                "2026-09-02T00:00:00Z",
                "연습 문장입니다.",
                "a".repeat(64),
                "recordings//1.wav",
                "audio/wav",
                1L,
                1,
                LearningFocus.PRONUNCIATION,
                null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisWorkerRequest(
                AnalysisWorkerRequest.SCHEMA_VERSION,
                UUID.randomUUID(),
                1L,
                2L,
                "2026-09-02T00:00:00Z",
                "연습 문장입니다.",
                "a".repeat(64),
                "recordings/1.wav",
                " ",
                1L,
                1,
                LearningFocus.PRONUNCIATION,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCompletedResultWithoutADeclaredOutcome() {
        assertThatThrownBy(() -> new AnalysisWorkerResult(
                AnalysisWorkerResult.SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                AnalysisStatus.COMPLETED,
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
                null,
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnalysisScoresInAFailedResult() {
        assertThatThrownBy(() -> new AnalysisWorkerResult(
                AnalysisWorkerResult.SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                AnalysisStatus.FAILED,
                null,
                "worker_failed",
                "안전하게 분석하지 못했습니다.",
                null,
                null,
                null,
                java.math.BigDecimal.TEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "worker-v1",
                "pipeline-v1",
                null,
                java.util.List.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
