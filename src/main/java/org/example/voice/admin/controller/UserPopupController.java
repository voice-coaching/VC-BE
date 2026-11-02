package org.example.voice.admin.controller;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.voice.admin.application.AdminCommunicationService;
import org.example.voice.admin.controller.dto.AdminCommunicationResponseDto.ActivePopupDto;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @Tag(name="사용자")
public class UserPopupController {
    private final AdminCommunicationService service;
    @GetMapping("/api/users/me/popup") @Operation(summary="현재 노출할 전체 팝업 조회")
    public ResponseEntity<ApiResponse<ActivePopupDto>> active(@AuthenticationPrincipal LoginUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("노출할 팝업을 조회했습니다.",ActivePopupDto.from(service.activePopup(user.id()))));
    }
}
