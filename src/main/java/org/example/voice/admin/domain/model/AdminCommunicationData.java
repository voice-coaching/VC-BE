package org.example.voice.admin.domain.model;
import java.time.OffsetDateTime;
import java.util.List;
import org.example.voice.admin.domain.type.*;
public final class AdminCommunicationData {
    private AdminCommunicationData() {}
    public record CampaignDraft(AdminAudience audience,AdminNotificationKind kind,String title,String body,String deepLink,List<Long> userIds) {}
    public record CampaignView(long id,long version,AdminAudience audience,AdminNotificationKind kind,String title,String body,
            String deepLink,List<Long> userIds,String status,long audienceCount,long sentCount,OffsetDateTime createdAt,OffsetDateTime sentAt) {}
    public record PopupDraft(String title,String body,String deepLink,int priority,OffsetDateTime startsAt,int durationDays,boolean enabled) {}
    public record PopupView(long id,long version,String title,String body,String deepLink,int priority,
            OffsetDateTime startsAt,OffsetDateTime endsAt,boolean enabled,OffsetDateTime createdAt) {}
}
