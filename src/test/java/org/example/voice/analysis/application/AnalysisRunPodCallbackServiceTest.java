package org.example.voice.analysis.application;

import org.example.voice.analysis.controller.dto.RunPodAnalysisResultCallbackRequestDto;
import org.example.voice.analysis.domain.entity.*;
import org.example.voice.analysis.domain.model.*;
import org.example.voice.analysis.domain.port.*;
import org.example.voice.analysis.domain.type.*;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.domain.port.RecordingDeletionScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.example.voice.analysis.infrastructure.runpod.RunPodContractTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisRunPodCallbackServiceTest {
    private final AnalysisResultReader reader = mock(AnalysisResultReader.class);
    private final AnalysisResultWriter writer = mock(AnalysisResultWriter.class);
    private final AnalysisSegmentWriter segments = mock(AnalysisSegmentWriter.class);
    private final AnalysisRequestOutboxJpaRepository outboxes = mock(AnalysisRequestOutboxJpaRepository.class);
    private final RunPodContract contract = new RunPodContract();
    private final RunPodAnalysisProperties properties = new RunPodAnalysisProperties();
    private final AnalysisResultIngestionService ingestion = new AnalysisResultIngestionService(reader, writer, segments, mock(RecordingDeletionScheduler.class));
    private final AnalysisRunPodCallbackService service = new AnalysisRunPodCallbackService(reader, writer, ingestion, properties, outboxes, contract);
    private AnalysisResult result;
    private String payload;
    private OffsetDateTime deadline;

    @BeforeEach
    void setup() {
        VoiceRecording recording = mock(VoiceRecording.class);
        when(recording.getId()).thenReturn(2L);
        when(recording.getSelected()).thenReturn(true);
        when(recording.getAudioSha256()).thenReturn("b".repeat(64));
        when(recording.getDurationMs()).thenReturn(1000);
        result = AnalysisResult.pending(recording, REQUEST);
        ReflectionTestUtils.setField(result, "id", 1L);
        deadline = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(40).withNano(0);
        result.assignExecution(EXECUTION, deadline);
        payload = payload(deadline);
        var outbox = AnalysisRequestOutbox.pendingHttp(REQUEST, EXECUTION, result, payload);
        when(reader.findForIngestion(1L)).thenReturn(Optional.of(result));
        when(outboxes.findByEventIdAndExecutionIdAndTransport(REQUEST.toString(), EXECUTION.toString(), "RUNPOD_HTTP"))
                .thenReturn(Optional.of(outbox));
    }

    @Test
    void claimIsBoundToPayloadAndServerCappedLease() {
        var response = service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        assertThat(response.workerInstanceId()).isEqualTo(WORKER.toString());
        assertThat(OffsetDateTime.parse(response.leaseExpiresAt())).isEqualTo(deadline);
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
        service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        contract.encode(response, "leaseResponse");
    }

    @Test
    void rejectsChangedPayloadAndWorkerTakeover() {
        expect("PAYLOAD_DIGEST_MISMATCH", () -> service.claim(1L, claim(WORKER.toString(), "0".repeat(64))));
        service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        expect("WORKER_CONFLICT", () -> service.claim(1L, claim(UUID.randomUUID().toString(), contract.digest(payload))));
    }

    @Test
    void heartbeatCannotClaimOrReviveExpiredLease() {
        var heartbeat = new AnalysisRunPodHeartbeatCommand(REQUEST, EXECUTION, WORKER.toString());
        expect("CLAIM_REQUIRED", () -> service.heartbeat(1L, heartbeat));
        service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        contract.encode(service.heartbeat(1L, heartbeat), "leaseResponse");
        ReflectionTestUtils.setField(result, "claimExpiresAt", OffsetDateTime.now().minusSeconds(1));
        expect("LEASE_EXPIRED", () -> service.heartbeat(1L, heartbeat));
        expect("LEASE_EXPIRED", () -> service.claim(1L, claim(WORKER.toString(), contract.digest(payload))));
    }

    @Test
    void rejectsStaleCancelledAndExpiredExecution() {
        expect("STALE_EXECUTION", () -> service.claim(1L, new AnalysisRunPodClaimCommand(REQUEST, UUID.randomUUID(), WORKER.toString(), contract.digest(payload))));
        when(result.getRecording().getSelected()).thenReturn(false);
        expect("ANALYSIS_CANCELLED", () -> service.claim(1L, claim(WORKER.toString(), contract.digest(payload))));
        when(result.getRecording().getSelected()).thenReturn(true);
        String expired = payload(OffsetDateTime.now().minusMinutes(1));
        var expiredOutbox = AnalysisRequestOutbox.pendingHttp(REQUEST, EXECUTION, result, expired);
        when(outboxes.findByEventIdAndExecutionIdAndTransport(REQUEST.toString(), EXECUTION.toString(), "RUNPOD_HTTP"))
                .thenReturn(Optional.of(expiredOutbox));
        expect("DEADLINE_EXCEEDED", () -> service.claim(1L, claim(WORKER.toString(), contract.digest(expired))));
    }

    @Test
    void resultRequiresClaimAndOwnerThenAcknowledgesIdenticalRetryAfterLeaseExpires() {
        UUID event = UUID.randomUUID();
        var command = command(result(event));
        expect("CLAIM_REQUIRED", () -> service.ingestResult(1L, command));
        service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        var wrongWorker = command(result(event).replace(WORKER.toString(), UUID.randomUUID().toString()));
        expect("WORKER_CONFLICT", () -> service.ingestResult(1L, wrongWorker));
        assertThat(service.ingestResult(1L, command)).isEqualTo(AnalysisResultIngestionDisposition.APPLIED);
        ReflectionTestUtils.setField(result, "claimExpiresAt", OffsetDateTime.now().minusSeconds(1));
        assertThat(service.ingestResult(1L, command)).isEqualTo(AnalysisResultIngestionDisposition.IGNORED_DUPLICATE);
        expect("RESULT_EVENT_CONFLICT", () -> service.ingestResult(1L, command(result(event).replace("worker1", "worker2"))));
        expect("RESULT_ALREADY_FINALIZED", () -> service.ingestResult(1L, command(result(UUID.randomUUID()))));
        verify(segments, times(1)).replaceForAnalysis(any(), any());
    }

    private AnalysisRunPodResultCommand command(String raw) {
        var json = contract.parse(raw.getBytes(StandardCharsets.UTF_8), "result");
        return contract.convert(json, RunPodAnalysisResultCallbackRequestDto.class).toCommand(contract.digest(json));
    }

    private String scoredResult() {
        return result(UUID.randomUUID()).replace("runpod-analysis-result.v1", "runpod-analysis-result.v2")
                .replace("\"failureReason\":null", """
                  "failureReason":null,"overallScore":78.0,"scoringEvidence":{
                  "rubricRevision":"clova-phone-rubric-v1","generator":"hyperclova",
                  "modelRevision":"%s","evidenceSha256":"%s","expectedPhoneCount":10,
                  "correctPhoneCount":7,"alignedPhoneCount":9,"unflaggedPhoneCount":9,
                  "alignmentScore":42.0,"coverageScore":18.0,"stabilityScore":18.0}
                  """.formatted("a".repeat(40), "c".repeat(64)));
    }

    @Test
    void storesValidatedClovaScoreAndAuditWithIdempotentAck() {
        service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        var scored = command(scoredResult());
        assertThat(service.ingestResult(1L, scored)).isEqualTo(AnalysisResultIngestionDisposition.APPLIED);
        assertThat(result.getOverallScore()).isEqualByComparingTo("78.0");
        assertThat(result.getClovaScoreEvidence()).containsEntry("rubricRevision", "clova-phone-rubric-v1");
        assertThat(service.ingestResult(1L, scored)).isEqualTo(AnalysisResultIngestionDisposition.IGNORED_DUPLICATE);
        verify(segments, times(1)).replaceForAnalysis(any(), any());
    }

    @Test
    void rejectsUnverifiedOrInvalidScoresBeforeChangingAnalysis() {
        service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        String valid = scoredResult();
        expect("VALIDATION_FAILED", () -> service.ingestResult(1L, command(valid.replace("\"overallScore\":78.0", "\"overallScore\":99.0"))));
        expect("VALIDATION_FAILED", () -> command(valid.replace("runpod-analysis-result.v2", "runpod-analysis-result.v1")));
        expect("VALIDATION_FAILED", () -> command(valid.replace("\"overallScore\":78.0,", "")));
        expect("VALIDATION_FAILED", () -> command(valid.replace("\"overallScore\":78.0", "\"overallScore\":101")));
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(result.getOverallScore()).isNull();
        assertThat(result.getLastResultEventId()).isNull();
    }

    private String detailedScoredResult() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(scoredResult());
        body.put("overallScore", 82.1);
        var audit = body.putObject("scoringEvidence");
        audit.put("rubricRevision", "clova-phone-rubric-v2"); audit.put("generator", "hyperclova");
        audit.put("modelRevision", "a".repeat(40)); audit.put("evidenceSha256", "b".repeat(64));
        audit.put("rubricSha256", org.example.voice.analysis.domain.model.ClovaScoreEvidence.POLICY_SHA256);
        audit.put("promptSha256", "c".repeat(64));
        audit.put("expectedPhoneCount", 4); audit.put("correctPhoneCount", 4);
        audit.put("alignedPhoneCount", 4); audit.put("unflaggedPhoneCount", 3);
        var rows = audit.putArray("criteria");
        for (String id : java.util.List.of("vowels", "plain_stops", "tense_stops", "aspirated_stops",
                "fricatives", "affricates", "nasals", "liquid", "coverage")) {
            boolean active = id.equals("vowels") || id.equals("coverage");
            var row = rows.addObject(); row.put("criterionId", id); row.put("sampleCount", active ? 4 : 0);
            row.put("matchedClear", active ? 3 : 0); row.put("matchedFlagged", active ? 1 : 0);
            row.put("substitutedClear", 0); row.put("substitutedFlagged", 0);
            row.put("deletedClear", 0); row.put("deletedFlagged", 0);
            if (active) row.put("level", id.equals("coverage") ? 4 : 3); else row.putNull("level");
        }
        return mapper.writeValueAsString(body);
    }

    @Test
    void storesDetailedRubricWithAbsentCategoriesAndIdempotentAck() throws Exception {
        service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        var command = command(detailedScoredResult());
        assertThat(service.ingestResult(1L, command)).isEqualTo(AnalysisResultIngestionDisposition.APPLIED);
        assertThat(result.getOverallScore()).isEqualByComparingTo("82.1");
        assertThat(result.getClovaScoreEvidence()).containsKeys("criteria", "rubricSha256", "promptSha256");
        assertThat(service.ingestResult(1L, command)).isEqualTo(AnalysisResultIngestionDisposition.IGNORED_DUPLICATE);
    }

    @Test
    void rejectsDetailedRubricTamperingBeforeStateChanges() throws Exception {
        service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        String raw = detailedScoredResult();
        for (String invalid : java.util.List.of(
                raw.replace("\"overallScore\":82.1", "\"overallScore\":100"),
                raw.replace("\"level\":3", "\"level\":4"),
                raw.replace("\"level\":null", "\"level\":4"),
                raw.replace("\"criterionId\":\"liquid\"", "\"criterionId\":\"vowels\""),
                raw.replace(org.example.voice.analysis.domain.model.ClovaScoreEvidence.POLICY_SHA256, "d".repeat(64)))) {
            expect("VALIDATION_FAILED", () -> service.ingestResult(1L, command(invalid)));
        }
        assertThat(result.getOverallScore()).isNull();
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
    }

    @Test
    void acceptsFailedResultWithoutInventedMediaEvidence() {
        service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        String raw = result(UUID.randomUUID()).replace("\"status\":\"COMPLETED\"", "\"status\":\"FAILED\"")
                .replace("\"outcome\":\"COMPLETED_NO_ISSUE\"", "\"outcome\":null")
                .replace("\"audioSha256\":\""+"b".repeat(64)+"\"", "\"audioSha256\":null")
                .replace("\"pipelineRevision\":\"pipeline1\"", "\"pipelineRevision\":null")
                .replace("\"failureCode\":null", "\"failureCode\":\"analysis_execution_failed_closed\"")
                .replace("\"failureReason\":null", "\"failureReason\":\"Analysis failed\"");
        assertThat(service.ingestResult(1L, command(raw))).isEqualTo(AnalysisResultIngestionDisposition.APPLIED);
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getAudioSha256()).isNull();
        assertThat(service.ingestResult(1L, command(raw))).isEqualTo(AnalysisResultIngestionDisposition.IGNORED_DUPLICATE);
        result.cancel("analysis_cancelled", "Cancelled");
        expect("ANALYSIS_CANCELLED", () -> service.ingestResult(1L, command(raw)));
    }

    @Test
    void rejectsNewResultAfterLeaseExpires() {
        service.claim(1L, claim(WORKER.toString(), contract.digest(payload)));
        ReflectionTestUtils.setField(result, "claimExpiresAt", OffsetDateTime.now().minusSeconds(1));
        expect("LEASE_EXPIRED", () -> service.ingestResult(1L, command(result(UUID.randomUUID()))));
        verifyNoInteractions(segments);
    }
    private AnalysisRunPodClaimCommand claim(String worker, String digest) {
        return new AnalysisRunPodClaimCommand(REQUEST, EXECUTION, worker, digest);
    }
    private void expect(String reason, Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(RunPodContractException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }
}
