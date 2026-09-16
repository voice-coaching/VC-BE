package org.example.voice.support.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.example.voice.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.example.voice.common.security.LoginUser;
import org.example.voice.support.application.SupportService;
import org.example.voice.support.controller.dto.SupportDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: 요청 형식 또는 페이지 범위 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED: 로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: 리소스 없음 또는 소유권 불일치")
})
public class SupportController {
    private final SupportService service;

    @GetMapping("/notices")
    public ApiResponse<Page<NoticeSummary>> notices(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("OK", Page.from(service.notices(page, size), NoticeSummary::from));
    }

    @GetMapping("/notices/{noticeId}")
    public ApiResponse<NoticeDetail> notice(@PathVariable Long noticeId) {
        return ApiResponse.success("OK", NoticeDetail.from(service.notice(noticeId)));
    }

    @PostMapping("/inquiries")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN: 접수가 제한된 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CONFLICT: 같은 Idempotency-Key의 다른 요청")
    })
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatedInquiry> create(@AuthenticationPrincipal LoginUser user,
            @Valid @RequestBody CreateInquiry request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ApiResponse.success("문의를 접수했습니다.", CreatedInquiry.from(service.create(user.id(), request.toDraft(), key)));
    }

    @GetMapping("/users/me/inquiries")
    public ApiResponse<Page<Inquiry>> inquiries(@AuthenticationPrincipal LoginUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("OK", Page.from(service.inquiries(user.id(), page, size), Inquiry::from));
    }

    @GetMapping("/users/me/inquiries/{inquiryId}")
    public ApiResponse<Inquiry> inquiry(@AuthenticationPrincipal LoginUser user, @PathVariable Long inquiryId) {
        return ApiResponse.success("OK", Inquiry.from(service.inquiry(user.id(), inquiryId)));
    }
}
