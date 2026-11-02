package org.example.voice.admin.controller.dto;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
public record AdminMemberWithdrawalRequestDto(@NotNull @Positive Long confirmationUserId,
        OffsetDateTime expectedUpdatedAt,@NotBlank @Size(max=500) String reason) {}
