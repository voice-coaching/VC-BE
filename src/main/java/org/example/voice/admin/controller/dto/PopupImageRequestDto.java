package org.example.voice.admin.controller.dto;

import jakarta.validation.constraints.*;

public record PopupImageRequestDto(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=500) String reason) {}
