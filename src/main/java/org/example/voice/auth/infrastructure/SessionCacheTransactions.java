package org.example.voice.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.port.LoginSessionCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class SessionCacheTransactions {
    private final LoginSessionCache cache;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public void changing(Long userId) {

        if (!cache.enabled()) return;
        // Materialize the PostgreSQL transaction before publishing its marker. Otherwise
        // bootstrap could mistake a not-yet-started DB transaction for a completed writer.
        jdbc.execute("SELECT 1");
        String change = cache.beginChange(userId);
        if (change == null) return; // Disabled optimization keeps the original DB-only behavior.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                // Unknown completion must keep its barrier. A later bootstrap can reconcile it.
                if (status == STATUS_COMMITTED || status == STATUS_ROLLED_BACK) cache.finishChange(userId, change);
            }
        });
    }
}
