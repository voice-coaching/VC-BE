package org.example.voice.user.controller.dto;

import org.example.voice.user.domain.model.WithdrawalResult;

import java.time.OffsetDateTime;

public record UserWithdrawalResponseDto(OffsetDateTime withdrawnAt) {
    public static UserWithdrawalResponseDto from(WithdrawalResult result) {
        return new UserWithdrawalResponseDto(result.withdrawnAt());
    }
}
