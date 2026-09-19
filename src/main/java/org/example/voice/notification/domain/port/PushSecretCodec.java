package org.example.voice.notification.domain.port;

import org.example.voice.notification.domain.model.NotificationData.PushDraft;

public interface PushSecretCodec {
    void requireConfigured();
    String encrypt(PushDraft draft);
    String fingerprint(PushDraft draft);
}
