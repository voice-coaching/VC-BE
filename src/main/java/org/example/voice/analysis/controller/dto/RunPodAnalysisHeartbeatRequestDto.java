package org.example.voice.analysis.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.voice.analysis.domain.model.AnalysisRunPodHeartbeatCommand;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RunPodAnalysisHeartbeatRequestDto(
        @NotBlank String schemaVersion,
        @NotNull UUID requestId,
        @NotNull UUID executionId,
        @NotBlank String workerInstanceId,
        @NotNull OffsetDateTime heartbeatAt
) {
    public AnalysisRunPodHeartbeatCommand toCommand() {
        return new AnalysisRunPodHeartbeatCommand(schemaVersion, requestId, executionId, workerInstanceId, heartbeatAt);
    }
}
