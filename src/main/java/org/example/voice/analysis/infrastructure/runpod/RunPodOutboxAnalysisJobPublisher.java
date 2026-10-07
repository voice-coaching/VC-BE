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

    @Override
    @Transactional
    public void publish(AnalysisWorkerRequest request) {
        persist(request, AnalysisExecutionProfile.CANONICAL_HANDOFF);
    }

    @Override
    @Transactional
    public void publish(AnalysisWorkerRequest request, AnalysisExecutionProfile profile) {
        persist(request, java.util.Objects.requireNonNull(profile, "profile"));
    }

    private void persist(AnalysisWorkerRequest request, AnalysisExecutionProfile profile) {
        if (!properties.isConfigured()) {
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
        if (profile != AnalysisExecutionProfile.CANONICAL_HANDOFF && profile != AnalysisExecutionProfile.CANONICAL_AUDIOVISUAL)
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        boolean canonical = true;
        AnalysisResult analysisResult = (canonical ? analysisResultRepository.findForIngestion(request.analysisId())
                : analysisResultRepository.findById(request.analysisId()))
                .orElseThrow(() -> new IllegalStateException("analysis result disappeared before outbox write"));
        if (!analysisResult.isForActiveRequest(request.eventId())) {
            throw new IllegalStateException("analysis request event does not match active analysis request");
        }
        boolean audiovisual = profile == AnalysisExecutionProfile.CANONICAL_AUDIOVISUAL;
        handoffSettings.reserveAdmissionBudget(audiovisual
                ? org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffSettings.AUDIOVISUAL_RESERVATION
                : org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffSettings.RESERVATION);
        if (canonical) assertCanonicalScope(analysisResult, request, audiovisual);
        UUID executionId = UUID.randomUUID();
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plus(properties.getExecutionTimeout())
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        analysisResult.assignExecution(executionId, deadline);
        if (audiovisual) analysisResult.assignAudiovisualProfile();
        else if (profile == AnalysisExecutionProfile.CANONICAL_HANDOFF) analysisResult.assignHandoffProfile();
        else if (canonical) analysisResult.assignCanonicalProfile();
        RunPodAnalysisJobRequest runPodRequest = canonical
                ? RunPodAnalysisJobRequest.canonicalFrom(request, executionId, analysisResult.getRecording().getId(), deadline)
                : RunPodAnalysisJobRequest.from(
                request,
                executionId,
                analysisResult.getRecording().getId(),
                deadline
        );
        if (profile == AnalysisExecutionProfile.CANONICAL_HANDOFF) runPodRequest=runPodRequest.asHandoff();
        if (audiovisual) {
            var receipt = preparations.find(analysisResult.getRecording().getId())
                    .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_MEDIA_BINDING_INVALID));
            if (!receipt.binds(request.audioSha256(),request.visualInput().sha256()))
                throw new BaseException(ErrorCode.ANALYSIS_MEDIA_BINDING_INVALID);
            var node = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(receipt);
            runPodRequest=runPodRequest.asAudiovisual(new RunPodAnalysisJobRequest.MediaPreparationInput(receipt,contract.digest(node)));
        }
        String payload = codec.encodeRequest(runPodRequest);
        if (codec.payloadBytes(payload) > properties.getMaximumPayloadBytes()) {
            throw new IllegalStateException("runpod_analysis_request_payload_size_invalid");
        }
        outboxRepository.save(AnalysisRequestOutbox.pendingHttp(
                request.eventId(),
                executionId,
                analysisResult,
                payload
        ));
        if (canonical) {
            // Flush pending session/profile/outbox changes before the registry's JDBC fences.
            // This is NOT a commit: registry failure rolls back the whole admission transaction.
            outboxRepository.flush();
            canonicalExecutions.registerCurrent(analysisResult.getId(), request.eventId(), executionId);
        }
    }

    private void assertCanonicalScope(AnalysisResult result, AnalysisWorkerRequest request, boolean audiovisual) {
        // A permanent profile mismatch must not look like a transient AI outage.
        // v6 stays closed until its verifier, lifecycle and reader are installed.
        if ((!audiovisual && (request.visualInput() != null || result.getRecording().getVisualObjectKey() != null))
                || (audiovisual && (request.visualInput() == null || result.getRecording().getVisualObjectKey() == null))) {
            throw new BaseException(ErrorCode.ANALYSIS_MEDIA_PROFILE_UNSUPPORTED);
        }
        if (result.getStatus() != AnalysisStatus.PENDING || result.getActiveExecutionId() != null
                || !canonicalScope.eligible(result, audiovisual)
                || request.learningFocus() != org.example.voice.practicecontent.domain.type.LearningFocus.PRONUNCIATION
                ) {
            // Initial rollout supports standalone pronunciation; course/title grading stays excluded.
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
    }
}
