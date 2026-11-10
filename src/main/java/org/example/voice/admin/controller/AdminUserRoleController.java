package org.example.voice.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.application.AdminUserRoleService;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.example.voice.admin.controller.dto.AdminUserRoleDto.*;

@RestController @RequestMapping("/api/admin/users") @RequiredArgsConstructor
public class AdminUserRoleController {
    private final AdminUserRoleService service;
    @PatchMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<ResponseDto>> change(@AuthenticationPrincipal LoginUser actor,
            @PathVariable long userId,@Valid @RequestBody RequestDto request) {
        var result=service.change(actor.id(),userId,request.expectedRole(),request.role(),request.reason());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("회원 권한을 변경했습니다.",new ResponseDto(result.userId(),result.role())));
    }
}
