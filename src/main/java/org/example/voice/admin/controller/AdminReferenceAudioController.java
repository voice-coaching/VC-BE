package org.example.voice.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.application.*;
import org.example.voice.admin.controller.dto.AdminAudioRequestDto;
import org.example.voice.admin.controller.dto.AdminAudioResponseDto;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor @Tag(name="관리자")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="파일 정보·필수 입력값 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403",description="현재 활성 ADMIN 권한 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="대상·소유 업로드 없음 또는 ANNOUNCER 범위 밖"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="문장 버전·원문·등록 내용 충돌 또는 업로드 만료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="422",description="ADMIN_AUDIO_INVALID: 실제 음성 파일 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="503",description="음성 저장소·디코더 또는 DB 사용 불가")
})
public class AdminReferenceAudioController {
    private final AdminReferenceAudioService service;
    private final AdminReferenceAudioTransactions transactions;

    @PostMapping("/contents/{contentId}/reference-audios/upload-url") @Operation(summary="아나운서 기준 음성 업로드 URL 발급")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="관리자·문장·해시에 연결된 업로드 생성")
    public ResponseEntity<ApiResponse<AdminAudioResponseDto.AdminAudioUploadResponseDto>> issue(@AuthenticationPrincipal LoginUser actor,@PathVariable long contentId,
            @Valid @RequestBody AdminAudioRequestDto.AdminAudioUploadRequestDto request) {
        return response(201,AdminAudioResponseDto.AdminAudioUploadResponseDto.from(service.issue(actor.id(),contentId,request.command())));
    }
    @PostMapping("/contents/{contentId}/reference-audios") @Operation(summary="검증한 아나운서 기준 음성 등록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="신규 음성 연결 완료. 동일 요청 재조회는 200")
    public ResponseEntity<ApiResponse<AdminAudioResponseDto.AudioDto>> register(@AuthenticationPrincipal LoginUser actor,@PathVariable long contentId,
            @Valid @RequestBody AdminAudioRequestDto.RegisterDto request) {
        var result=service.complete(actor.id(),contentId,request.uploadId(),request.version(),request.speakerName(),request.primary(),request.reason());
        return response(result.created()?201:200,AdminAudioResponseDto.AudioDto.from(result));
    }
    @GetMapping("/contents/{contentId}/reference-audios") @Operation(summary="비공개 포함 아나운서 기준 음성 목록")
    public ResponseEntity<ApiResponse<AdminAudioResponseDto.ListDto>> list(@AuthenticationPrincipal LoginUser actor,@PathVariable long contentId) {
        return response(200,AdminAudioResponseDto.ListDto.from(transactions.list(actor.id(),contentId)));
    }
    @PatchMapping("/reference-audios/{audioId}/primary") @Operation(summary="아나운서 대표 기준 음성 지정")
    public ResponseEntity<ApiResponse<AdminAudioResponseDto.AudioDto>> primary(@AuthenticationPrincipal LoginUser actor,@PathVariable long audioId,
            @Valid @RequestBody AdminAudioRequestDto.PrimaryDto request) {
        return response(200,AdminAudioResponseDto.AudioDto.from(transactions.primary(actor.id(),audioId,request.version(),request.reason())));
    }
    @GetMapping("/reference-audios/{audioId}/playback-url") @Operation(summary="관리자 기준 음성 미리듣기 URL 발급")
    public ResponseEntity<ApiResponse<AdminAudioResponseDto.PlaybackDto>> playback(@AuthenticationPrincipal LoginUser actor,@PathVariable long audioId) {
        return response(200,AdminAudioResponseDto.PlaybackDto.from(service.playback(actor.id(),audioId)));
    }
    private static <T> ResponseEntity<ApiResponse<T>> response(int status,T data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.success("관리자 기준 음성 요청을 처리했습니다.",data));
    }
}
