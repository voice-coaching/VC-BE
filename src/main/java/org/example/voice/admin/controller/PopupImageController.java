package org.example.voice.admin.controller;

import java.io.IOException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.application.*;
import org.example.voice.admin.controller.dto.AdminCommunicationResponseDto.PopupDto;
import org.example.voice.admin.controller.dto.PopupImageRequestDto;
import org.example.voice.common.exception.*;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/popups/{popupId}/image")
public class PopupImageController {
    private final PopupImageService images;
    private final PopupImageTransactions transactions;
    private final AdminCommunicationService communications;
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @io.swagger.v3.oas.annotations.Operation(summary="팝업 이미지 첨부·교체",description="PNG/JPEG 최대 3MiB. 활성 관리자와 팝업 version 검사.")
    public ResponseEntity<ApiResponse<PopupDto>> upload(@AuthenticationPrincipal LoginUser actor,@PathVariable long popupId,
            @RequestParam long version,@RequestParam String reason,@RequestPart MultipartFile file) throws IOException {
        if(file.isEmpty()||file.getSize()>3*1024*1024 || !java.util.Set.of("image/png","image/jpeg").contains(file.getContentType()))
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        images.upload(actor.id(),popupId,version,reason,file.getBytes());return response(actor.id(),popupId);
    }
    @DeleteMapping
    @io.swagger.v3.oas.annotations.Operation(summary="팝업 이미지 첨부 해제")
    public ResponseEntity<ApiResponse<PopupDto>> remove(@AuthenticationPrincipal LoginUser actor,@PathVariable long popupId,
            @Valid @RequestBody PopupImageRequestDto request) {
        transactions.remove(actor.id(),popupId,request.version(),request.reason());return response(actor.id(),popupId);
    }
    private ResponseEntity<ApiResponse<PopupDto>> response(long actor,long popup) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("팝업 이미지를 변경했습니다.",PopupDto.from(communications.popup(actor,popup))));
    }
}
