package org.example.voice.analysis.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.voice.analysis.domain.model.AnalysisRunPodHeartbeatCommand;

import java.util.UUID;

public record RunPodAnalysisHeartbeatRequestDto(
         UUID requestId,
         UUID executionId,
         String workerInstanceId
) {
    public AnalysisRunPodHeartbeatCommand toCommand() {
        return new AnalysisRunPodHeartbeatCommand(requestId, executionId, workerInstanceId);
    }
}
