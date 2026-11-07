package org.example.voice.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.application.AdminTitleExamPolicyService;
import org.example.voice.admin.controller.dto.*;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.title.domain.TitleRank;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/title-exam-policies")
public class AdminTitleExamPolicyController {
    private final AdminTitleExamPolicyService service;
    public record PolicyListDto(List<AdminTitleExamPolicyResponseDto> items) {}

    @GetMapping @Operation(summary = "칭호별 승급 시험 문제와 합격 기준 조회")
    public ResponseEntity<ApiResponse<PolicyListDto>> list(@AuthenticationPrincipal LoginUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("승급 시험 설정을 조회했습니다.",
                new PolicyListDto(service.list(actor.id()).stream().map(AdminTitleExamPolicyResponseDto::from).toList())));
    }
    @PatchMapping("/{targetRank}") @Operation(summary = "승급 시험 문제와 합격 점수 변경")
    public ResponseEntity<ApiResponse<AdminTitleExamPolicyResponseDto>> edit(@AuthenticationPrincipal LoginUser actor,
            @PathVariable TitleRank targetRank, @Valid @RequestBody AdminTitleExamPolicyRequestDto body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("승급 시험 설정을 저장했습니다.",
                AdminTitleExamPolicyResponseDto.from(service.edit(actor.id(), targetRank, body.version(), body.practiceContentId(), body.contentVersion(), body.passingScore(), body.reason()))));
    }
}
