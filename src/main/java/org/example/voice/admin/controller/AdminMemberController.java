package org.example.voice.admin.controller;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.example.voice.admin.application.AdminMemberWithdrawalService;
import org.example.voice.admin.controller.dto.AdminMemberWithdrawalRequestDto;
import org.example.voice.admin.controller.dto.AdminMemberWithdrawalResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.voice.admin.application.AdminMemberService;
import org.example.voice.admin.controller.dto.AdminMemberResponseDto.*;
import org.example.voice.admin.controller.dto.AdminResponseDto.PageDto;
import org.example.voice.admin.domain.type.AdminUserResource;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/users") @Tag(name="관리자")
public class AdminMemberController {
    private final AdminMemberService service;
    private final AdminMemberWithdrawalService withdrawal;
    @GetMapping("/resolve") @Operation(summary="회원 번호 또는 분석 UUID로 회원 찾기")
    public ResponseEntity<ApiResponse<List<IdentifierMatchDto>>> resolve(@AuthenticationPrincipal LoginUser actor,@RequestParam String identifier) {
        return ok(service.resolve(actor.id(),identifier).stream().map(IdentifierMatchDto::from).toList());
    }
    @GetMapping("/{userId}/overview") @Operation(summary="회원별 관련 기록 현황과 코칭 사용량")
    public ResponseEntity<ApiResponse<MemberOverviewDto>> overview(@AuthenticationPrincipal LoginUser actor,@PathVariable long userId) {
        return ok(MemberOverviewDto.from(service.overview(actor.id(),userId)));
    }
    @GetMapping("/{userId}/records") @Operation(summary="회원별 분류별 관련 기록 조회")
    public ResponseEntity<ApiResponse<PageDto<MemberRecordDto>>> records(@AuthenticationPrincipal LoginUser actor,@PathVariable long userId,
            @RequestParam AdminUserResource resource,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ok(PageDto.from(service.records(actor.id(),userId,resource,page,size),MemberRecordDto::from));
    }
    @PostMapping("/{userId}/withdrawal") @Operation(summary="관리자 회원 탈퇴 처리")
    public ResponseEntity<ApiResponse<AdminMemberWithdrawalResponseDto>> withdraw(
            @AuthenticationPrincipal LoginUser actor,@PathVariable long userId,
            @Valid @RequestBody AdminMemberWithdrawalRequestDto request) {
        var result=withdrawal.withdraw(actor.id(),userId,request.confirmationUserId(),request.expectedUpdatedAt(),request.reason());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("회원 탈퇴 처리를 완료했습니다.",AdminMemberWithdrawalResponseDto.from(result)));
    }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("회원 관련 기록을 조회했습니다.",data)); }
}
