package org.example.voice.auth.infrastructure;

import org.example.voice.auth.domain.port.LoginSessionCache;
import org.example.voice.auth.domain.entity.RefreshToken;
import org.example.voice.auth.exception.SessionStoreUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionCacheTransactionsTest {
    private final LoginSessionCache cache=mock(LoginSessionCache.class);
    private final SessionCacheTransactions transactions=new SessionCacheTransactions(cache,mock(org.springframework.jdbc.core.JdbcTemplate.class));
    @AfterEach void clear() {TransactionSynchronizationManager.clear();}

    @Test void commitAndRollbackReleaseOnlyAfterDatabaseCompletion() {
        when(cache.enabled()).thenReturn(true);
        for(int status:new int[]{TransactionSynchronization.STATUS_COMMITTED,TransactionSynchronization.STATUS_ROLLED_BACK}) {
            reset(cache);when(cache.enabled()).thenReturn(true);TransactionSynchronizationManager.clear();
            TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();
            when(cache.beginChange(7L)).thenReturn("change");transactions.changing(7L);
            verify(cache,never()).finishChange(any(),any());
            for(var callback:TransactionSynchronizationManager.getSynchronizations())callback.afterCompletion(status);
            verify(cache).finishChange(7L,"change");
        }
    }
    @Test void uncertainDatabaseCompletionKeepsBarrier() {
        when(cache.enabled()).thenReturn(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();
        when(cache.beginChange(7L)).thenReturn("change");transactions.changing(7L);
        for(var callback:TransactionSynchronizationManager.getSynchronizations())callback.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        verify(cache,never()).finishChange(any(),any());
    }
    @Test void mutationOutsideApplicationTransactionIsRejected() {
        assertThatThrownBy(()->transactions.changing(7L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(cache);
    }
    @Test void unavailableInvalidationPreventsAnyDatabaseMutation() {
        when(cache.enabled()).thenReturn(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();
        when(cache.beginChange(7L)).thenThrow(new SessionStoreUnavailableException());
        var repository=mock(RefreshTokenJpaRepository.class);
        var writer=new RefreshTokenWriterImpl(repository,transactions);
        var token=RefreshToken.issue(7L,"hash","sid",OffsetDateTime.now().plusHours(1),OffsetDateTime.now());
        assertThatThrownBy(()->writer.save(token)).isInstanceOf(SessionStoreUnavailableException.class);
        assertThatThrownBy(()->writer.delete(token)).isInstanceOf(SessionStoreUnavailableException.class);
        assertThatThrownBy(()->writer.deleteByUserId(7L)).isInstanceOf(SessionStoreUnavailableException.class);
        verifyNoInteractions(repository);
    }
}
