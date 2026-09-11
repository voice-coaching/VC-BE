package org.example.voice.analysis.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.voice.analysis.domain.model.AnalysisRunPodClaimCommand;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RunPodAnalysisClaimRequestDto(
        @NotBlank String schemaVersion,
        @NotNull UUID requestId,
        @NotNull UUID executionId,
        @NotBlank String workerInstanceId,
        @NotNull OffsetDateTime claimedUntil
) {
    public AnalysisRunPodClaimCommand toCommand() {
        return new AnalysisRunPodClaimCommand(schemaVersion, requestId, executionId, workerInstanceId, claimedUntil);
    }
}
