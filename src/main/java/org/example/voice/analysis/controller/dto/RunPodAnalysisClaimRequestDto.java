package org.example.voice.analysis.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.voice.analysis.domain.model.AnalysisRunPodClaimCommand;


import java.util.UUID;

public record RunPodAnalysisClaimRequestDto(
        @NotNull UUID requestId,
        @NotNull UUID executionId,
        @NotBlank String workerInstanceId,
        @NotBlank String requestPayloadSha256
) {
    public AnalysisRunPodClaimCommand toCommand() {
        return new AnalysisRunPodClaimCommand(requestId, executionId, workerInstanceId, requestPayloadSha256);
    }
}
