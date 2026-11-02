package org.example.voice.admin.controller.dto;
import java.time.OffsetDateTime;
import org.example.voice.admin.application.AdminMemberWithdrawalService.Result;
public record AdminMemberWithdrawalResponseDto(long userId,OffsetDateTime withdrawnAt,boolean alreadyWithdrawn) {
    public static AdminMemberWithdrawalResponseDto from(Result r) { return new AdminMemberWithdrawalResponseDto(r.userId(),r.withdrawnAt(),r.alreadyWithdrawn()); }
}
