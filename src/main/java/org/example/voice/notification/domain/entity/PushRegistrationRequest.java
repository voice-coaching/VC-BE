package org.example.voice.notification.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "push_registration_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "key_hash"}))
public class PushRegistrationRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 64) private String keyHash;
    @Column(nullable = false, length = 64) private String bodyHash;
    @Column(nullable = false) private Long subscriptionId;
    @Column(nullable = false) private OffsetDateTime createdAt;
    @Column(length = 100) private String deviceName;
    @Column(nullable = false) private OffsetDateTime subscriptionCreatedAt;
    public PushRegistrationRequest(Long userId, String keyHash, String bodyHash, PushSubscription subscription, OffsetDateTime now) {
        this.userId = userId; this.keyHash = keyHash; this.bodyHash = bodyHash; this.subscriptionId = subscription.getId(); this.createdAt = now;
        this.deviceName = subscription.getDeviceName(); this.subscriptionCreatedAt = subscription.getCreatedAt();
    }
    public org.example.voice.notification.domain.model.NotificationData.Subscription view() {
        return new org.example.voice.notification.domain.model.NotificationData.Subscription(subscriptionId, deviceName, true,
                subscriptionCreatedAt.withOffsetSameInstant(java.time.ZoneOffset.UTC));
    }
}
