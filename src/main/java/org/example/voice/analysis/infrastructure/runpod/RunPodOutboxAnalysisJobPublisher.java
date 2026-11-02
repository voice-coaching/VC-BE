package org.example.voice.analysis.infrastructure.runpod;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisRequestOutbox;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.analysis.domain.port.CanonicalExecutionRegistry;
import org.example.voice.analysis.infrastructure.AnalysisRequestOutboxJpaRepository;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis", name = "transport", havingValue = "runpod_http")
public class RunPodOutboxAnalysisJobPublisher implements AnalysisJobPublisher {

    private final AnalysisRequestOutboxJpaRepository outboxRepository;
    private final AnalysisResultJpaRepository analysisResultRepository;
    private final RunPodAnalysisPayloadCodec codec;
    private final RunPodAnalysisProperties properties;
    private final CanonicalExecutionRegistry canonicalExecutions;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffSettings handoffSettings;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalRequestScope canonicalScope;
    private final org.example.voice.training.domain.port.RecordingMediaPreparationStore preparations;
    private final RunPodContract contract;
    private final org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness videoReadiness;

    @Override
    @Transactional
    public void publish(AnalysisWorkerRequest request) {
        persist(request, AnalysisExecutionProfile.CANONICAL_AUDIOVISUAL);
    }

    @Override
    @Transactional
    public void publish(AnalysisWorkerRequest request, AnalysisExecutionProfile profile) {
        persist(request, java.util.Objects.requireNonNull(profile, "profile"));
    }

    private void persist(AnalysisWorkerRequest request, AnalysisExecutionProfile profile) {

        AnalysisResult analysisResult = analysisResultRepository.findForIngestion(request.analysisId())
                .orElseThrow(() -> new IllegalStateException("analysis result disappeared before outbox write"));

        boolean audiovisual = request.visualInput()!=null;

        UUID executionId = UUID.randomUUID();
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plus(properties.getExecutionTimeout())
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        analysisResult.assignExecution(executionId, deadline);
        analysisResult.assignAudiovisualProfile();
        RunPodAnalysisJobRequest runPodRequest = RunPodAnalysisJobRequest.from(
                request,
                executionId,
                analysisResult.getRecording().getId(),
                deadline
        );
        var receipt = preparations.find(analysisResult.getRecording().getId()).orElse(null);
        RunPodAnalysisJobRequest.MediaPreparationInput prepared=null;
        if (receipt != null) {

            var node = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(receipt);
            prepared=new RunPodAnalysisJobRequest.MediaPreparationInput(receipt,contract.digest(node));
        } else {}
        runPodRequest=runPodRequest.asV6(prepared);
        String payload = codec.encodeRequest(runPodRequest);

        outboxRepository.save(AnalysisRequestOutbox.pendingHttp(
                request.eventId(),
                executionId,
                analysisResult,
                payload
        ));
        // Registry failure rolls back this entire admission transaction.
        outboxRepository.flush();
        canonicalExecutions.registerCurrent(analysisResult.getId(), request.eventId(), executionId);
    }

}
