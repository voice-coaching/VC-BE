package org.example.voice.notification.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import org.example.voice.notification.domain.model.NotificationData.Subscription;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "push_subscriptions")
public class PushSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, unique = true, length = 64) private String endpointHash;
    @Column(nullable = false, columnDefinition = "text") private String encryptedPayload;
    @Column(length = 100) private String deviceName;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private OffsetDateTime createdAt;
    @Column(nullable = false) private OffsetDateTime updatedAt;
    public static PushSubscription create(Long userId, String hash, String encrypted, String deviceName, OffsetDateTime now) {
        var s = new PushSubscription(); s.userId = userId; s.endpointHash = hash; s.createdAt = now;
        s.refresh(encrypted, deviceName, now); return s;
    }
    public void refresh(String encrypted, String deviceName, OffsetDateTime now) {
        this.encryptedPayload = encrypted; this.deviceName = deviceName; this.active = true;
        this.updatedAt = updatedAt != null && !now.isAfter(updatedAt) ? updatedAt.plusNanos(1000) : now;
    }
    public void deactivate() { active = false; }
    public Subscription view() { return new Subscription(id, deviceName, active, createdAt.withOffsetSameInstant(ZoneOffset.UTC)); }
}
