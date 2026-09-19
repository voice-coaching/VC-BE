package org.example.voice.notification.domain.port;

import org.example.voice.notification.domain.entity.*;
import org.example.voice.notification.domain.model.NotificationData.Page;
import java.time.OffsetDateTime;
import java.util.*;

public interface NotificationStore {
    Optional<NotificationPreference> preferences(Long userId);
    void save(NotificationPreference preference);
    Page list(Long userId, boolean unreadOnly, int page, int size);
    Optional<UserNotification> notification(Long userId, Long id);
    int readAll(Long userId, OffsetDateTime now);
    boolean hasEvent(Long userId, String key);
    void save(UserNotification notification);
    List<Long> reminderUsers(Long after, int size);
    Optional<PushSubscription> subscriptionByHash(String hash);
    Optional<PushSubscription> subscription(Long userId, Long id);
    long subscriptionCount(Long userId);
    PushSubscription save(PushSubscription subscription);
    void delete(PushSubscription subscription);
    Optional<PushRegistrationRequest> request(Long userId, String keyHash);
    void save(PushRegistrationRequest request);
}
