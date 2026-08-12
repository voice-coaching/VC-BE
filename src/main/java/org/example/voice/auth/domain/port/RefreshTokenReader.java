package org.example.voice.auth.domain.port;

import org.example.voice.auth.domain.entity.RefreshToken;
import java.util.Optional;
import java.time.OffsetDateTime;

public interface RefreshTokenReader {
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);
    Optional<RefreshToken> findByUserIdForUpdate(Long userId);
    boolean isActiveSession(Long userId, String sessionId, OffsetDateTime now);
}
