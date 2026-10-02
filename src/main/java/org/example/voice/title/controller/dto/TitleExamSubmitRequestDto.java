package org.example.voice.title.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TitleExamSubmitRequestDto(
        @NotNull @Positive Long analysisId
) {
}
