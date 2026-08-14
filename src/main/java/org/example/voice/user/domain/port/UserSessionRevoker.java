package org.example.voice.user.domain.port;

public interface UserSessionRevoker {
    void revokeAll(Long userId);
}
