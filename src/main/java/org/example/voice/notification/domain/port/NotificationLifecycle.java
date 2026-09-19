package org.example.voice.notification.domain.port;

import java.time.OffsetDateTime;

public interface NotificationLifecycle {
    void eraseForUser(Long userId);
    void recordPushResponse(Long subscriptionId, OffsetDateTime attemptedRevision, int status);
}
