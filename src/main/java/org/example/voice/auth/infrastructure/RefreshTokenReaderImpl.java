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
    private final org.example.voice.auth.domain.port.LoginSessionCache cache;

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
        // A transaction may retain an older DB snapshot: never publish it as current state.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            return repository.existsByUserIdAndSessionIdAndExpiresAtAfter(userId, sessionId, now);
        }
        var lookup = cache.lookup(userId);
        if (lookup.kind() == org.example.voice.auth.domain.port.LoginSessionCache.Kind.CACHED) {
            return lookup.session() != null && lookup.session().accepts(sessionId, now);
        }
        if (lookup.kind() == org.example.voice.auth.domain.port.LoginSessionCache.Kind.BYPASS) {
            return repository.existsByUserIdAndSessionIdAndExpiresAtAfter(userId, sessionId, now);
        }
        var state = repository.findSessionState(userId);
        cache.publish(userId, lookup.ticket(), state);
        return state.filter(session -> session.accepts(sessionId, now)).isPresent();
    }
}
