package org.example.voice.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.voice.admin.application.AdminCourseService;
import org.example.voice.admin.controller.dto.*;
import org.example.voice.admin.domain.model.AdminData.Query;
import org.example.voice.common.security.LoginUser;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.exception.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/courses") @RequiredArgsConstructor
public class AdminCourseController {
    private final AdminCourseService service;
    @GetMapping
    public ResponseEntity<ApiResponse<AdminResponseDto.PageDto<AdminCourseResponseDto>>> list(@AuthenticationPrincipal LoginUser actor,@RequestParam(required=false)String keyword,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) {
        return ok(AdminResponseDto.PageDto.from(service.list(actor.id(),new Query(keyword,page,size)),AdminCourseResponseDto::from));
    }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<AdminCourseResponseDto>> read(@AuthenticationPrincipal LoginUser actor,@PathVariable long id) {
        return ok(AdminCourseResponseDto.from(service.read(actor.id(),id)));
    }
    @PostMapping public ResponseEntity<ApiResponse<AdminCourseResponseDto>> create(@AuthenticationPrincipal LoginUser actor,@RequestBody AdminCourseRequestDto request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiResponse.success("클래스를 등록했습니다.",AdminCourseResponseDto.from(service.create(actor.id(),request.course()==null?null:request.course().toData(),request.reason()))));
    }
    @PatchMapping("/{id}") public ResponseEntity<ApiResponse<AdminCourseResponseDto>> edit(@AuthenticationPrincipal LoginUser actor,@PathVariable long id,@RequestBody AdminCourseRequestDto request) {
        if(request.version()==null)throw new BaseException(ErrorCode.VALIDATION_ERROR);
        return ok(AdminCourseResponseDto.from(service.edit(actor.id(),id,request.version(),request.course()==null?null:request.course().toData(),request.reason())));
    }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("클래스 관리",data)); }
}
