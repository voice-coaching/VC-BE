package org.example.voice.admin.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.voice.admin.application.AdminCommunicationService;
import org.example.voice.admin.controller.dto.AdminCommunicationRequestDto.*;
import org.example.voice.admin.controller.dto.AdminCommunicationResponseDto.*;
import org.example.voice.admin.controller.dto.AdminResponseDto.PageDto;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor @Tag(name="관리자")
public class AdminCommunicationController {
    private final AdminCommunicationService service;
    @GetMapping("/notification-campaigns") @Operation(summary="전체·개별 알림 목록")
    public ResponseEntity<ApiResponse<PageDto<CampaignDto>>> campaigns(@AuthenticationPrincipal LoginUser actor,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return response(200,PageDto.from(service.campaigns(actor.id(),page,size),CampaignDto::from));
    }
    @GetMapping("/notification-campaigns/{campaignId}") @Operation(summary="발송 대상 및 알림 상세")
    public ResponseEntity<ApiResponse<CampaignDto>> campaign(@AuthenticationPrincipal LoginUser actor,@PathVariable long campaignId) {
        return response(200,CampaignDto.from(service.campaign(actor.id(),campaignId)));
    }
    @PostMapping("/notification-campaigns") @Operation(summary="전체·개별 알림 초안 등록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="알림 초안 생성")
    public ResponseEntity<ApiResponse<CampaignDto>> create(@AuthenticationPrincipal LoginUser actor,@Valid @RequestBody CampaignCreateDto request) {
        return response(201,CampaignDto.from(service.create(actor.id(),request.command(),request.reason())));
    }
    @PatchMapping("/notification-campaigns/{campaignId}") @Operation(summary="발송 전 알림 초안 수정")
    public ResponseEntity<ApiResponse<CampaignDto>> edit(@AuthenticationPrincipal LoginUser actor,@PathVariable long campaignId,@Valid @RequestBody CampaignEditDto request) {
        return response(200,CampaignDto.from(service.edit(actor.id(),campaignId,request.version(),request.command(),request.reason())));
    }
    @DeleteMapping("/notification-campaigns/{campaignId}") @Operation(summary="발송 전 알림 초안 삭제")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal LoginUser actor,@PathVariable long campaignId,@Valid @RequestBody CampaignDeleteDto request) {
        service.delete(actor.id(),campaignId,request.version(),request.reason());return response(200,null);
    }
    @PostMapping("/notification-campaigns/{campaignId}/dispatch") @Operation(summary="앱 내 알림 발송")
    public ResponseEntity<ApiResponse<CampaignDto>> dispatch(@AuthenticationPrincipal LoginUser actor,@PathVariable long campaignId,@Valid @RequestBody CampaignDispatchDto request) {
        return response(200,CampaignDto.from(service.dispatch(actor.id(),campaignId,request.version(),request.reason())));
    }
    @GetMapping("/popups") @Operation(summary="기간 지정 전체 팝업 목록")
    public ResponseEntity<ApiResponse<PageDto<PopupDto>>> popups(@AuthenticationPrincipal LoginUser actor,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return response(200,PageDto.from(service.popups(actor.id(),page,size),PopupDto::from));
    }
    @GetMapping("/popups/{popupId}") @Operation(summary="기간 지정 전체 팝업 상세")
    public ResponseEntity<ApiResponse<PopupDto>> popup(@AuthenticationPrincipal LoginUser actor,@PathVariable long popupId) {
        return response(200,PopupDto.from(service.popup(actor.id(),popupId)));
    }
    @PostMapping("/popups") @Operation(summary="기간 지정 전체 팝업 등록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="팝업 생성")
    public ResponseEntity<ApiResponse<PopupDto>> createPopup(@AuthenticationPrincipal LoginUser actor,@Valid @RequestBody PopupCreateDto request) {
        return response(201,PopupDto.from(service.createPopup(actor.id(),request.popup().command(),request.reason())));
    }
    @PatchMapping("/popups/{popupId}") @Operation(summary="전체 팝업 수정·노출 중지")
    public ResponseEntity<ApiResponse<PopupDto>> editPopup(@AuthenticationPrincipal LoginUser actor,@PathVariable long popupId,@Valid @RequestBody PopupEditDto request) {
        return response(200,PopupDto.from(service.editPopup(actor.id(),popupId,request.version(),request.popup().command(),request.reason())));
    }
    private static <T> ResponseEntity<ApiResponse<T>> response(int status,T data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.success("관리자 알림 요청을 처리했습니다.",data));
    }
}
