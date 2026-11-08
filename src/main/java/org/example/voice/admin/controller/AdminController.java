package org.example.voice.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.application.*;
import org.example.voice.admin.controller.dto.AdminRequestDto.*;
import org.example.voice.admin.controller.dto.AdminResponseDto.*;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.common.response.ApiResponse;
import org.example.voice.common.security.LoginUser;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.user.domain.type.*;
import org.example.voice.support.domain.entity.Inquiry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.List;

@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
@Tag(name="관리자",description="현재 활성 ADMIN 전용. 테스트 이용등급 admin은 관리 권한이 아닙니다.")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="입력값 또는 검색 조건 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="로그인 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403",description="현재 관리자 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="대상 없음 또는 관리 범위 밖"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="ADMIN_EDIT_CONFLICT: 버전 또는 상태 충돌"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="503",description="ADMIN_STORE_UNAVAILABLE: 저장소 오류")
})
public class AdminController {
    private final AdminAccessService access;
    private final AdminReadService reads;
    private final AdminContentService contents;
    private final AdminSupportService support;

    @GetMapping("/me") @Operation(summary="현재 관리자 접근 정보")
    public ResponseEntity<ApiResponse<AccessDto>> me(@AuthenticationPrincipal LoginUser actor) {
        return ok(AccessDto.from(access.require(actor.id())));
    }
    @GetMapping("/users") @Operation(summary="관리자 회원 목록 조회")
    public ResponseEntity<ApiResponse<PageDto<UserDto>>> users(@AuthenticationPrincipal LoginUser actor,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) UserStatus status,
            @RequestParam(required=false) UserRole role,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ok(PageDto.from(reads.users(actor.id(),new UserFilter(new Query(keyword,page,size),status,role)),UserDto::from));
    }
    @GetMapping("/users/{userId}") @Operation(summary="관리자 회원 상세 조회")
    public ResponseEntity<ApiResponse<UserDto>> user(@AuthenticationPrincipal LoginUser actor,@PathVariable long userId) {
        return ok(UserDto.from(reads.user(actor.id(),userId)));
    }
    @GetMapping("/content-categories") @Operation(summary="콘텐츠 등록용 카테고리 조회")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> categories(@AuthenticationPrincipal LoginUser actor,@RequestParam ContentType type) {
        return ok(reads.categories(actor.id(),type).stream().map(CategoryDto::from).toList());
    }
    @GetMapping("/contents") @Operation(summary="비공개 포함 공용 콘텐츠 목록 조회")
    public ResponseEntity<ApiResponse<PageDto<ContentDto>>> contents(@AuthenticationPrincipal LoginUser actor,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) ContentType type,
            @RequestParam(required=false) String category,@RequestParam(required=false) PublishStatus status,
            @RequestParam(required=false) Difficulty difficulty,@RequestParam(defaultValue="false") boolean excludeClassPractice,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ok(PageDto.from(reads.contents(actor.id(),new ContentFilter(new Query(keyword,page,size),type,category,status,difficulty,excludeClassPractice)),ContentDto::from));
    }
    @GetMapping("/contents/{contentId}") @Operation(summary="공용 콘텐츠 상세 조회")
    public ResponseEntity<ApiResponse<ContentDto>> content(@AuthenticationPrincipal LoginUser actor,@PathVariable long contentId) {
        return ok(ContentDto.from(reads.content(actor.id(),contentId)));
    }
    @PostMapping("/contents") @Operation(summary="공용 콘텐츠 초안 생성")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="공용 초안 생성 완료")
    public ResponseEntity<ApiResponse<ContentDto>> createContent(@AuthenticationPrincipal LoginUser actor,@Valid @RequestBody ContentCreateDto request) {
        return created(ContentDto.from(contents.create(actor.id(),request.content().command(),request.reason())));
    }
    @PatchMapping("/contents/{contentId}") @Operation(summary="공용 콘텐츠 수정")
    public ResponseEntity<ApiResponse<ContentDto>> editContent(@AuthenticationPrincipal LoginUser actor,@PathVariable long contentId,@Valid @RequestBody ContentEditDto request) {
        return ok(ContentDto.from(contents.edit(actor.id(),contentId,request.version(),request.content().command(),request.reason())));
    }
    @DeleteMapping("/contents/{contentId}") @Operation(summary="공용 콘텐츠 삭제")
    public ResponseEntity<ApiResponse<ContentDeletionDto>> deleteContent(@AuthenticationPrincipal LoginUser actor,@PathVariable long contentId,
            @Valid @RequestBody ContentDeleteDto request) {
        return ok(ContentDeletionDto.from(contents.delete(actor.id(),contentId,request.version(),request.reason())));
    }
    @PatchMapping("/contents/{contentId}/publication") @Operation(summary="콘텐츠 공개·숨김")
    public ResponseEntity<ApiResponse<ContentDto>> publication(@AuthenticationPrincipal LoginUser actor,@PathVariable long contentId,@Valid @RequestBody PublicationDto request) {
        return ok(ContentDto.from(contents.publication(actor.id(),contentId,request.version(),request.status(),request.reason())));
    }
    @GetMapping("/notices") @Operation(summary="관리자 공지 목록 조회")
    public ResponseEntity<ApiResponse<PageDto<NoticeDto>>> notices(@AuthenticationPrincipal LoginUser actor,@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Boolean published,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ok(PageDto.from(reads.notices(actor.id(),new NoticeFilter(new Query(keyword,page,size),published)),NoticeDto::from));
    }
    @GetMapping("/notices/{noticeId}") @Operation(summary="비공개 포함 공지 상세 조회")
    public ResponseEntity<ApiResponse<NoticeDto>> notice(@AuthenticationPrincipal LoginUser actor,@PathVariable long noticeId) {
        return ok(NoticeDto.from(reads.notice(actor.id(),noticeId)));
    }
    @PostMapping("/notices") @Operation(summary="공지 생성")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="공지 생성 완료")
    public ResponseEntity<ApiResponse<NoticeDto>> createNotice(@AuthenticationPrincipal LoginUser actor,@Valid @RequestBody NoticeCreateDto request) {
        return created(NoticeDto.from(support.createNotice(actor.id(),request.notice().command(),request.reason())));
    }
    @PatchMapping("/notices/{noticeId}") @Operation(summary="공지 수정·고정·공개 설정")
    public ResponseEntity<ApiResponse<NoticeDto>> editNotice(@AuthenticationPrincipal LoginUser actor,@PathVariable long noticeId,@Valid @RequestBody NoticeEditDto request) {
        return ok(NoticeDto.from(support.editNotice(actor.id(),noticeId,request.version(),request.notice().command(),request.reason())));
    }
    @DeleteMapping("/notices/{noticeId}") @Operation(summary="공지 삭제")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(@AuthenticationPrincipal LoginUser actor,@PathVariable long noticeId,@Valid @RequestBody NoticeDeleteDto request) {
        support.deleteNotice(actor.id(),noticeId,request.version(),request.reason());return ok(null);
    }
    @GetMapping("/inquiries") @Operation(summary="전체 문의 목록 조회")
    public ResponseEntity<ApiResponse<PageDto<InquirySummaryDto>>> inquiries(@AuthenticationPrincipal LoginUser actor,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) Inquiry.Status status,@RequestParam(required=false) String category,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ok(PageDto.from(reads.inquiries(actor.id(),new InquiryFilter(new Query(keyword,page,size),status,category)),InquirySummaryDto::from));
    }
    @GetMapping("/inquiries/{inquiryId}") @Operation(summary="관리자 문의 상세 조회")
    public ResponseEntity<ApiResponse<InquiryDto>> inquiry(@AuthenticationPrincipal LoginUser actor,@PathVariable long inquiryId) {
        return ok(InquiryDto.from(reads.inquiry(actor.id(),inquiryId)));
    }
    @PatchMapping("/inquiries/{inquiryId}/answer") @Operation(summary="문의 답변 저장")
    public ResponseEntity<ApiResponse<InquiryDto>> answer(@AuthenticationPrincipal LoginUser actor,@PathVariable long inquiryId,@Valid @RequestBody AnswerDto request) {
        return ok(InquiryDto.from(support.answer(actor.id(),inquiryId,request.version(),request.answer(),request.reason())));
    }
    @PatchMapping("/inquiries/{inquiryId}/status") @Operation(summary="문의 처리 상태 전환")
    public ResponseEntity<ApiResponse<InquiryDto>> status(@AuthenticationPrincipal LoginUser actor,@PathVariable long inquiryId,@Valid @RequestBody InquiryStatusDto request) {
        return ok(InquiryDto.from(support.status(actor.id(),inquiryId,request.version(),request.status(),request.reason())));
    }
    @GetMapping("/audit-logs") @Operation(summary="관리자 작업 이력 조회")
    public ResponseEntity<ApiResponse<PageDto<AuditDto>>> audits(@AuthenticationPrincipal LoginUser actor,
            @RequestParam(required=false) Long actorId,@RequestParam(required=false) String action,@RequestParam(required=false) String targetType,
            @RequestParam(required=false) String targetId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ok(PageDto.from(reads.audits(actor.id(),new AuditFilter(page,size,actorId,action,targetType,targetId,from,to)),AuditDto::from));
    }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("관리자 요청을 처리했습니다.",data));
    }
    private static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiResponse.success("관리자 리소스를 생성했습니다.",data));
    }
}
