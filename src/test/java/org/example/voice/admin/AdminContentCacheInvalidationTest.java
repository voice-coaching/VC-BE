package org.example.voice.admin;

import org.example.voice.admin.infrastructure.AdminContentCacheInvalidation;
import org.example.voice.practicecontent.infrastructure.cache.PracticeContentCacheNames;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

class AdminContentCacheInvalidationTest {
    @Test void invalidationRunsOnlyAfterCommit() {
        var cache=new ConcurrentMapCacheManager();var detail=cache.getCache(PracticeContentCacheNames.DETAIL);detail.put(1L,"old");
        TransactionSynchronizationManager.initSynchronization();
        try {
            new AdminContentCacheInvalidation(cache).afterContentChange();
            assertThat(detail.get(1L)).isNotNull();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertThat(detail.get(1L)).isNull();
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }

    @Test void cacheFailureDoesNotReportAnAlreadyCommittedWriteAsFailed() {
        org.springframework.cache.CacheManager failed=new org.springframework.cache.CacheManager() {
            @Override public org.springframework.cache.Cache getCache(String name) { throw new IllegalStateException("unavailable"); }
            @Override public java.util.Collection<String> getCacheNames() { return java.util.List.of(); }
        };
        TransactionSynchronizationManager.initSynchronization();
        try {
            new AdminContentCacheInvalidation(failed).afterContentChange();
            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit())).doesNotThrowAnyException();
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }
}
