package org.example.voice.admin;

import org.example.voice.admin.infrastructure.AdminContentCacheInvalidation;
import org.example.voice.practicecontent.infrastructure.cache.PracticeContentCacheNames;
import org.example.voice.practicecontent.infrastructure.cache.ContentCatalogCacheCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

class AdminContentCacheInvalidationTest {
    @Test void invalidationRunsOnlyAfterCommit() {
        var cache=new ConcurrentMapCacheManager();var detail=cache.getCache(PracticeContentCacheNames.DETAIL);detail.put(1L,"old");
        TransactionSynchronizationManager.initSynchronization();
        try {
            var catalog=new ContentCatalogCacheCoordinator(cache);String generation=catalog.generation();
            var facets=cache.getCache(PracticeContentCacheNames.FACETS);facets.put("test","old");
            new AdminContentCacheInvalidation(cache,catalog).afterContentChange();
            assertThat(detail.get(1L)).isNotNull();
            assertThat(catalog.generation()).isEqualTo(generation);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertThat(detail.get(1L)).isNull();
            assertThat(facets.get("test")).isNull();
            assertThat(catalog.generation()).isNotEqualTo(generation);
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }

    @Test void cacheFailureDoesNotReportAnAlreadyCommittedWriteAsFailed() {
        org.springframework.cache.CacheManager failed=new org.springframework.cache.CacheManager() {
            @Override public org.springframework.cache.Cache getCache(String name) { throw new IllegalStateException("unavailable"); }
            @Override public java.util.Collection<String> getCacheNames() { return java.util.List.of(); }
        };
        TransactionSynchronizationManager.initSynchronization();
        try {
            new AdminContentCacheInvalidation(failed,new ContentCatalogCacheCoordinator(failed)).afterContentChange();
            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit())).doesNotThrowAnyException();
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }
}
