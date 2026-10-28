package org.example.voice.payment.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentCancellationRequestDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 200)
        String reason) {}
