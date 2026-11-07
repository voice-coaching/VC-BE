package org.example.voice.admin.controller.dto;

import jakarta.validation.constraints.*;

public record AdminTitleExamPolicyRequestDto(@NotNull @Positive Long practiceContentId,
        @NotNull @PositiveOrZero Long contentVersion,
        @NotNull @Min(1) @Max(100) Integer passingScore, @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 500) String reason) {}
