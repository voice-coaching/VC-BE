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
        @JsonInclude(JsonInclude.Include.NON_NULL) MediaInput video,
        OffsetDateTime deadlineAt
) {
    static final String SCHEMA_VERSION = "voice-coaching.runpod-analysis-request.v1";

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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MediaInput(
            String objectKey,
            String mimeType,
            String sha256,
            Long fileSizeBytes,
            Integer durationMs
    ) {
    }
}
