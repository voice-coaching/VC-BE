package org.example.voice.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.port.RefreshTokenWriter;
import org.example.voice.user.domain.port.UserSessionRevoker;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserSessionRevokerImpl implements UserSessionRevoker {

    private final RefreshTokenWriter refreshTokenWriter;

    @Override
    public void revokeAll(Long userId) {
        refreshTokenWriter.deleteByUserId(userId);
    }
}
