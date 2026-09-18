package org.example.voice.user.domain.port;

public interface LoginIdentityRevoker {
    void revokeForUser(Long userId);
}
