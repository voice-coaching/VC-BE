package org.example.voice.auth.domain.port;

import org.example.voice.auth.domain.entity.RefreshToken;

public interface RefreshTokenWriter {
    RefreshToken save(RefreshToken refreshToken);
    void delete(RefreshToken refreshToken);
    void deleteByUserId(Long userId);
}
