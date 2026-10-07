package org.example.voice.analysis.infrastructure.runpod;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

record RunPodAnalysisJobRequest(
        String schemaVersion,
        UUID requestId,
        UUID executionId,
        Long analysisId,
        Long recordingId,
        Long contentId,
        String learningFocus,
        String promptRevision,
        String scriptText,
        String scriptSha256,
        MediaInput audio,
        MediaInput video,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        OffsetDateTime deadlineAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String resultSchemaVersion,
        @JsonInclude(JsonInclude.Include.NON_NULL) String analysisProfile,
        @JsonInclude(JsonInclude.Include.NON_NULL) MediaPreparationInput mediaPreparation
) {
    static final String SCHEMA_VERSION = RunPodContract.REQUEST_V1;
    RunPodAnalysisJobRequest(String schemaVersion, UUID requestId, UUID executionId, Long analysisId,
                             Long recordingId, Long contentId, String learningFocus, String promptRevision,
                             String scriptText, String scriptSha256, MediaInput audio, MediaInput video,
                             OffsetDateTime deadlineAt, String resultSchemaVersion, String analysisProfile) {
        this(schemaVersion,requestId,executionId,analysisId,recordingId,contentId,learningFocus,promptRevision,
                scriptText,scriptSha256,audio,video,deadlineAt,resultSchemaVersion,analysisProfile,null);
    }

    // Preserve the legacy constructor and serialized field order; v1 has no routing fields.
    RunPodAnalysisJobRequest(String schemaVersion, UUID requestId, UUID executionId, Long analysisId,
                             Long recordingId, Long contentId, String learningFocus, String promptRevision,
                             String scriptText, String scriptSha256, MediaInput audio, MediaInput video,
                             OffsetDateTime deadlineAt) {
        this(schemaVersion, requestId, executionId, analysisId, recordingId, contentId, learningFocus,
                promptRevision, scriptText, scriptSha256, audio, video, deadlineAt, null, null);
    }

    static RunPodAnalysisJobRequest from(AnalysisWorkerRequest request, UUID executionId, Long recordingId,
                                         OffsetDateTime deadlineAt) {
        return new RunPodAnalysisJobRequest(
                SCHEMA_VERSION,
                request.eventId(),
                executionId,
                request.analysisId(),
                recordingId,
                request.contentId(),
                request.learningFocus().name(),
                request.promptRevision(),
                request.scriptText(),
                request.scriptSha256(),
                new MediaInput(
                        request.audioObjectKey(),
                        request.mimeType(),
                        request.audioSha256(),
                        request.fileSizeBytes(),
                        request.durationMs()
                ),
                request.visualInput() == null
                        ? null
                        : new MediaInput(
                        request.visualInput().objectKey(),
                        request.visualInput().mimeType(),
                        request.visualInput().sha256(),
                        request.visualInput().fileSizeBytes(),
                        null
                ),
                deadlineAt
        );
    }

    static RunPodAnalysisJobRequest canonicalFrom(AnalysisWorkerRequest request, UUID executionId,
                                                  Long recordingId, OffsetDateTime deadlineAt) {
        var legacy = from(request, executionId, recordingId, deadlineAt);
        return new RunPodAnalysisJobRequest(RunPodContract.REQUEST_V2, legacy.requestId(), legacy.executionId(),
                legacy.analysisId(), legacy.recordingId(), legacy.contentId(), legacy.learningFocus(),
                legacy.promptRevision(), legacy.scriptText(), legacy.scriptSha256(), legacy.audio(), legacy.video(),
                legacy.deadlineAt(), RunPodContract.RESULT_V4, "CANONICAL_FROZEN_20260928_V4");
    }

    RunPodAnalysisJobRequest asHandoff() {
        return new RunPodAnalysisJobRequest(RunPodContract.REQUEST_V3,requestId,executionId,analysisId,recordingId,contentId,learningFocus,promptRevision,scriptText,scriptSha256,audio,video,deadlineAt,RunPodContract.RESULT_V5,RunPodContract.HANDOFF_PROFILE);
    }

    RunPodAnalysisJobRequest asAudiovisual(MediaPreparationInput preparation) {
        return new RunPodAnalysisJobRequest(RunPodContract.REQUEST_V4,requestId,executionId,analysisId,recordingId,
                contentId,learningFocus,promptRevision,scriptText,scriptSha256,audio,video,deadlineAt,
                RunPodContract.RESULT_V6,RunPodContract.AUDIOVISUAL_PROFILE,preparation);
    }

    record MediaPreparationInput(org.example.voice.training.domain.model.MediaPreparationData receipt, String receiptSha256) {}

    record MediaInput(
            String objectKey,
            String mimeType,
            String sha256,
            Long fileSizeBytes,
            Integer durationMs
    ) {
    }
}
