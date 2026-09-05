package org.example.voice.analysis.domain.model;

import org.example.voice.analysis.domain.type.AnalysisOutcome;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisWorkerPayloadTest {

    @Test
    void acceptsV4ResultWithSignedContextRawPathsAndMedia() {
        AnalysisClosedBetaContext context = new AnalysisClosedBetaContext(
                AnalysisClosedBetaContext.SCHEMA_VERSION,
                9L,
                7L,
                50L
        );
        AnalysisClosedBetaDebug debug = new AnalysisClosedBetaDebug(
                AnalysisClosedBetaDebug.SCHEMA_VERSION,
                context,
                "COMPLETE",
                "recordings/50.wav",
                null,
                "/restricted/source.wav",
                "/restricted/decoded.wav",
                null,
                "cmF3",
                "ZGVjb2RlZA==",
                null
        );

        AnalysisWorkerResult result = new AnalysisWorkerResult(
                AnalysisWorkerResult.SCHEMA_VERSION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                35L,
                AnalysisStatus.COMPLETED,
                AnalysisOutcome.COMPLETED_NO_ISSUE,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                "worker-v2",
                "seungun-v2",
                "a".repeat(64),
                List.of(),
                null,
                Map.of("schema_version", "korean_phone_ctc.production_analysis.v2"),
                debug
        );

        assertThat(result.closedBetaDebug().context()).isEqualTo(context);
        assertThat(result.closedBetaDebug().audioMediaBase64()).isEqualTo("cmF3");
    }

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
                "b".repeat(64),
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
                "b".repeat(64),
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
                "b".repeat(64),
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
                "b".repeat(64),
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
                null,
                "worker-v1",
                "pipeline-v1",
                null,
                java.util.List.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
