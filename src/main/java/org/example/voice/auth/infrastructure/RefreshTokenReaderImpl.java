package org.example.voice.auth.infrastructure;

import org.example.voice.auth.domain.port.RefreshTokenReader;
import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.entity.RefreshToken;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenReaderImpl implements RefreshTokenReader {
    private final RefreshTokenJpaRepository repository;

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return repository.findByTokenHashForUpdate(tokenHash);
    }

    @Override
    public Optional<RefreshToken> findByUserIdForUpdate(Long userId) {
        return repository.findByUserIdForUpdate(userId);
    }

    @Override
    public boolean isActiveSession(Long userId, String sessionId, java.time.OffsetDateTime now) {
        return repository.existsByUserIdAndSessionIdAndExpiresAtAfter(userId, sessionId, now);
    }
}
