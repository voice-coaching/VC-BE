package org.example.voice.admin.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.example.voice.admin.domain.type.*;

@Entity @Getter @Table(name="admin_notification_campaigns")
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AdminNotificationCampaign {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AdminAudience audience;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AdminNotificationKind kind;
    @Column(nullable=false,length=200) private String title;
    @Column(nullable=false,length=1000) private String body;
    @Column(nullable=false,length=255) private String deepLink;
    @ElementCollection @CollectionTable(name="admin_notification_recipients",joinColumns=@JoinColumn(name="campaign_id"))
    @Column(name="user_id",nullable=false) private Set<Long> userIds=new HashSet<>();
    @Column(nullable=false) private long createdBy;
    @Column(nullable=false) private OffsetDateTime createdAt;
    private OffsetDateTime sentAt;
    @Column(nullable=false) private long sentCount;

    public static AdminNotificationCampaign create(AdminAudience audience,AdminNotificationKind kind,String title,String body,
            String link,Set<Long> users,long actor,OffsetDateTime now) {
        var c=new AdminNotificationCampaign();c.audience=audience;c.kind=kind;c.title=title;c.body=body;c.deepLink=link;
        c.userIds.addAll(users);c.createdBy=actor;c.createdAt=now;return c;
    }
    public void sent(long count,OffsetDateTime now) {
        if(sentAt!=null)throw new IllegalStateException("Already dispatched");
        sentCount=count;sentAt=now;
    }
}
