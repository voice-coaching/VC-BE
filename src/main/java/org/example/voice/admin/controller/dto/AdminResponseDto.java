package org.example.voice.admin.controller.dto;

import org.example.voice.admin.application.AdminReadService.Member;
import org.example.voice.admin.domain.model.AdminData.*;
import org.example.voice.practicecontent.domain.type.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

public final class AdminResponseDto {
    private AdminResponseDto() {}
    public record PageDto<T>(List<T> items,int page,int size,long totalElements,int totalPages,boolean hasNext) {
        public static <S,T> PageDto<T> from(Page<S> p,Function<S,T> mapper) {
            return new PageDto<>(p.items().stream().map(mapper).toList(),p.page(),p.size(),p.totalElements(),p.totalPages(),p.hasNext());
        }
    }
    public record AccessDto(long id,String nickname,String role) {
        public static AccessDto from(Access a) { return new AccessDto(a.id(),a.nickname(),a.role()); }
    }
    public record UserDto(long id,String maskedEmail,String nickname,String status,String role,OffsetDateTime createdAt,
                          String membershipType,OffsetDateTime membershipExpiresAt,String membershipEnvironment,List<String> loginProviders) {
        public static UserDto from(Member m) {
            var u=m.user();var membership=m.membership();
            return new UserDto(u.id(),u.email(),u.nickname(),u.status(),u.role(),u.createdAt(),membership.type(),membership.expiresAt(),membership.environment(),m.loginProviders());
        }
    }
    public record ContentDto(long id,long version,ContentType type,LearningFocus focus,String category,Difficulty difficulty,
            String title,String description,String scriptText,List<String> targetPronunciations,Integer estimatedSeconds,
            PublishStatus status,OffsetDateTime publishedAt,OffsetDateTime updatedAt) {
        public static ContentDto from(ContentView c) { return new ContentDto(c.id(),c.version(),c.type(),c.focus(),c.category(),c.difficulty(),
                c.title(),c.description(),c.scriptText(),c.targetPronunciations(),c.estimatedSeconds(),c.status(),c.publishedAt(),c.updatedAt()); }
    }
    public record CategoryDto(ContentType type,String code,String label,int sortOrder) {
        public static CategoryDto from(Category c) { return new CategoryDto(c.type(),c.code(),c.label(),c.sortOrder()); }
    }
    public record AdminSectionResponseDto(String title,List<String> paragraphs) {}
    public record NoticeDto(long id,long version,String title,String summary,List<AdminSectionResponseDto> sections,
                             boolean pinned,boolean published,OffsetDateTime publishedAt) {
        public static NoticeDto from(NoticeView n) { return new NoticeDto(n.id(),n.version(),n.title(),n.summary(),
                n.sections().stream().map(s -> new AdminSectionResponseDto(s.title(),s.paragraphs())).toList(),n.pinned(),n.published(),n.publishedAt()); }
    }
    public record InquirySummaryDto(long id,long version,long userId,String category,String subject,String status,OffsetDateTime createdAt) {
        public static InquirySummaryDto from(InquiryView i) { return new InquirySummaryDto(i.id(),i.version(),i.userId(),i.category(),i.subject(),i.status(),i.createdAt()); }
    }
    public record InquiryDto(long id,long version,long userId,String category,String subject,String body,Long relatedSessionId,
            String replyEmail,String status,String answer,OffsetDateTime createdAt,OffsetDateTime answeredAt) {
        public static InquiryDto from(InquiryView i) { return new InquiryDto(i.id(),i.version(),i.userId(),i.category(),i.subject(),i.body(),i.relatedSessionId(),
                i.replyEmail(),i.status(),i.answer(),i.createdAt(),i.answeredAt()); }
    }
    public record AuditDto(long id,long actorId,String action,String targetType,String targetId,String reason,
                            String changedFields,String outcome,String correlationId,OffsetDateTime createdAt) {
        public static AuditDto from(AuditView a) { return new AuditDto(a.id(),a.actorId(),a.action(),a.targetType(),a.targetId(),a.reason(),a.changedFields(),a.outcome(),a.correlationId(),a.createdAt()); }
    }
}
