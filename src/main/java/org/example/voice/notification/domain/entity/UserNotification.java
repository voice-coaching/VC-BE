package org.example.voice.notification.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import org.example.voice.notification.domain.NotificationException;
import org.example.voice.notification.domain.model.NotificationData.Item;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_notifications")
public class UserNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = true) private Long userId;
    @Column(nullable = true, length = 50) private String type;
    @Column(nullable = true, length = 200) private String title;
    @Column(nullable = true, length = 1000) private String body;
    @Column(nullable = true, length = 255) private String deepLink;
    @Column(nullable = true, length = 100) private String deduplicationKey;
    private OffsetDateTime readAt;
    @Column(nullable = true) private OffsetDateTime createdAt;

    public static UserNotification reminder(Long userId, LocalDate date, OffsetDateTime now) {
        return create(userId, "PRACTICE_REMINDER", "오늘 연습을 시작해 볼까요?", "홈에서 연습할 콘텐츠를 골라 보세요.",
                "/home", "practice:" + date, now);
    }
    public static UserNotification create(Long userId, String type, String title, String body, String deepLink,
                                          String deduplicationKey, OffsetDateTime now) {

        var n = new UserNotification(); n.userId = userId; n.type = type; n.title = title; n.body = body;
        n.deepLink = deepLink; n.deduplicationKey = deduplicationKey; n.createdAt = now; return n;
    }
    public void markRead(OffsetDateTime now) { if (readAt == null) readAt = now; }
    public Item view() { return new Item(id, type, title, body, deepLink,
            readAt == null ? null : readAt.withOffsetSameInstant(ZoneOffset.UTC), createdAt.withOffsetSameInstant(ZoneOffset.UTC)); }
}
