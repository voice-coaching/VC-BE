package org.example.voice.admin.controller.dto;

import jakarta.validation.constraints.*;
import org.example.voice.user.domain.type.UserRole;

public final class AdminUserRoleDto {
    private AdminUserRoleDto() {}
    public record RequestDto(@NotNull UserRole expectedRole,@NotNull UserRole role,
                             @NotBlank @Size(max=500) String reason) {}
    public record ResponseDto(long userId,UserRole role) {}
}
