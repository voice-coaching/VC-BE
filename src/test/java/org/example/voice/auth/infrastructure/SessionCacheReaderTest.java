package org.example.voice.auth.infrastructure;

import org.example.voice.auth.domain.model.LoginSessionState;
import org.example.voice.auth.domain.port.LoginSessionCache;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionCacheReaderTest {
    private final RefreshTokenJpaRepository repository=mock(RefreshTokenJpaRepository.class);
    private final LoginSessionCache cache=mock(LoginSessionCache.class);
    private final RefreshTokenReaderImpl reader=new RefreshTokenReaderImpl(repository,cache);
    private final OffsetDateTime now=OffsetDateTime.now();

    @Test void cachedCurrentSessionAvoidsAllDatabaseCallsButRejectsDifferentAndExpiredSessions() {
        when(cache.lookup(7L)).thenReturn(new LoginSessionCache.Lookup(LoginSessionCache.Kind.CACHED,new LoginSessionState("current",now.plusMinutes(1)),null));
        assertThat(reader.isActiveSession(7L,"current",now)).isTrue();
        assertThat(reader.isActiveSession(7L,"previous",now)).isFalse();
        assertThat(reader.isActiveSession(7L,"current",now.plusMinutes(1))).isFalse();
        verifyNoInteractions(repository);
    }
    @Test void missCachesDatabaseCurrentSessionInsteadOfThePresentedSid() {
        var state=Optional.of(new LoginSessionState("current",now.plusMinutes(1)));
        when(cache.lookup(7L)).thenReturn(new LoginSessionCache.Lookup(LoginSessionCache.Kind.LOAD,null,"ticket"));
        when(repository.findSessionState(7L)).thenReturn(state);
        assertThat(reader.isActiveSession(7L,"previous",now)).isFalse();
        verify(cache).publish(7L,"ticket",state);
        verify(repository,never()).existsByUserIdAndSessionIdAndExpiresAtAfter(any(),any(),any());
    }
    @Test void unavailableOrPendingCacheUsesAuthoritativeDatabaseValidation() {
        when(cache.lookup(7L)).thenReturn(LoginSessionCache.Lookup.bypass());
        when(repository.existsByUserIdAndSessionIdAndExpiresAtAfter(7L,"current",now)).thenReturn(true);
        assertThat(reader.isActiveSession(7L,"current",now)).isTrue();
        verify(cache,never()).publish(any(),any(),any());
    }
    @Test void absenceIsCachedAndNeverAuthenticates() {
        when(cache.lookup(7L)).thenReturn(new LoginSessionCache.Lookup(LoginSessionCache.Kind.CACHED,null,null));
        assertThat(reader.isActiveSession(7L,"current",now)).isFalse();
        verifyNoInteractions(repository);
    }
    @Test void transactionalSnapshotIsNeverPublishedAsCurrent() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            reader.isActiveSession(7L,"current",now);
            verifyNoInteractions(cache);verify(repository).existsByUserIdAndSessionIdAndExpiresAtAfter(7L,"current",now);
        } finally {org.springframework.transaction.support.TransactionSynchronizationManager.clear();}
    }
}
