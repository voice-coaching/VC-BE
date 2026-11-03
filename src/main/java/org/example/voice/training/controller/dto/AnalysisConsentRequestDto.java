package org.example.voice.training.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.voice.training.domain.model.AnalysisConsentData;

public record AnalysisConsentRequestDto(
         Boolean accepted,
          String policyRevision
) {
    public AnalysisConsentData toData() {
        return new AnalysisConsentData(accepted, policyRevision);
    }
}
