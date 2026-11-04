package org.example.voice.admin.controller.dto;
import java.time.OffsetDateTime;
import org.example.voice.admin.application.AdminMemberDeletionService.Result;
public record AdminMemberDeletionResponseDto(long userId,OffsetDateTime deletedAt,boolean alreadyDeleted) {
    public static AdminMemberDeletionResponseDto from(Result r) { return new AdminMemberDeletionResponseDto(r.userId(),r.deletedAt(),r.alreadyDeleted()); }
}
