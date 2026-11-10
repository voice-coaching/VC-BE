package org.example.voice.admin.domain.port;

import java.time.Duration;

public interface PopupImageStorage {
    void upload(String key,byte[] png);
    void delete(String key);
    String url(String key,Duration lifetime);
}
