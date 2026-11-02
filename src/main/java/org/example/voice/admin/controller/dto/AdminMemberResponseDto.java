package org.example.voice.admin.controller.dto;
import java.time.OffsetDateTime;
import java.util.*;
import org.example.voice.admin.application.AdminMemberService.Overview;
import org.example.voice.admin.domain.model.AdminMemberData.*;
import org.example.voice.admin.domain.type.AdminUserResource;
import org.example.voice.coaching.domain.CoachingUsage;
public final class AdminMemberResponseDto {
    private AdminMemberResponseDto() {}
    public record MemberRecordDto(Map<String,Object> fields) { public static MemberRecordDto from(RecordView r) { return new MemberRecordDto(r.fields()); } }
    public record IdentifierMatchDto(long userId,String source) { public static IdentifierMatchDto from(IdentifierMatch m) { return new IdentifierMatchDto(m.userId(),m.source()); } }
    public record ResourceCountDto(AdminUserResource resource,long count) {}
    public record AdminCoachingUsageDto(String membershipType,String environment,boolean unlimited,OffsetDateTime startsAt,OffsetDateTime expiresAt,
            Integer limit,long used,long reserved,Long remaining,boolean canRequestCoaching) {
        static AdminCoachingUsageDto from(CoachingUsage u) { return u==null?null:new AdminCoachingUsageDto(u.membershipType(),u.environment(),u.unlimited(),u.startsAt(),u.expiresAt(),u.limit(),u.used(),u.reserved(),u.remaining(),u.canRequestCoaching()); }
    }
    public record MemberOverviewDto(AdminResponseDto.UserDto user,List<ResourceCountDto> counts,AdminCoachingUsageDto coachingUsage,String usageUnavailableReason) {
        public static MemberOverviewDto from(Overview o) { return new MemberOverviewDto(AdminResponseDto.UserDto.from(o.member()),
                o.counts().stream().map(c->new ResourceCountDto(c.resource(),c.count())).toList(),AdminCoachingUsageDto.from(o.coachingUsage()),o.usageUnavailableReason()); }
    }
}
