package org.example.voice.admin.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity @Getter @Table(name="admin_popups") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AdminPopup {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @Column(nullable=false,length=200) private String title;
    @Column(nullable=false,length=1000) private String body;
    @Column(length=255) private String deepLink;
    @Column(nullable=false) private int priority;
    @Column(nullable=false) private OffsetDateTime startsAt;
    @Column(nullable=false) private OffsetDateTime endsAt;
    @Column(nullable=false) private boolean enabled;
    @Column(nullable=false) private long createdBy;
    @Column(nullable=false) private OffsetDateTime createdAt;
    public static AdminPopup create(long actor,OffsetDateTime now) {
        var p=new AdminPopup();p.createdBy=actor;p.createdAt=now;return p;
    }
    public void edit(String title,String body,String link,int priority,OffsetDateTime start,int days,boolean enabled) {
        this.title=title;this.body=body;this.deepLink=link;this.priority=priority;
        this.startsAt=start;this.endsAt=start.plusDays(days);this.enabled=enabled;
    }
}
