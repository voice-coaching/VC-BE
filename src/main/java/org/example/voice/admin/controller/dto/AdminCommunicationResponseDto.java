package org.example.voice.admin.controller.dto;
import java.time.OffsetDateTime;
import java.util.List;
import org.example.voice.admin.domain.model.AdminCommunicationData.*;
import org.example.voice.admin.domain.type.*;
public final class AdminCommunicationResponseDto {
    private AdminCommunicationResponseDto() {}
    public record CampaignDto(long id,long version,AdminAudience audience,AdminNotificationKind kind,String title,String body,
            String deepLink,List<Long> userIds,String status,long audienceCount,long sentCount,OffsetDateTime createdAt,OffsetDateTime sentAt) {
        public static CampaignDto from(CampaignView c) { return new CampaignDto(c.id(),c.version(),c.audience(),c.kind(),c.title(),c.body(),c.deepLink(),c.userIds(),c.status(),c.audienceCount(),c.sentCount(),c.createdAt(),c.sentAt()); }
    }
    public record PopupDto(long id,long version,String title,String body,String deepLink,int priority,
            OffsetDateTime startsAt,OffsetDateTime endsAt,boolean enabled,OffsetDateTime createdAt) {
        public static PopupDto from(PopupView p) { return new PopupDto(p.id(),p.version(),p.title(),p.body(),p.deepLink(),p.priority(),p.startsAt(),p.endsAt(),p.enabled(),p.createdAt()); }
    }
    public record ActivePopupDto(Long id,String title,String body,String deepLink,OffsetDateTime startsAt,OffsetDateTime endsAt) {
        public static ActivePopupDto from(PopupView p) { return p==null?null:new ActivePopupDto(p.id(),p.title(),p.body(),p.deepLink(),p.startsAt(),p.endsAt()); }
    }
}
