package org.example.voice.practicecontent.infrastructure;

import org.example.voice.practicecontent.domain.model.ContentCatalogData.*;
import org.example.voice.practicecontent.domain.model.*;
import org.example.voice.practicecontent.domain.type.*;
import org.example.voice.practicecontent.infrastructure.cache.*;
import org.junit.jupiter.api.*;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachedContentCatalogReaderTest {
    final ContentCatalogPersistence source=mock(ContentCatalogPersistence.class);
    final ConcurrentMapCacheManager caches=new ConcurrentMapCacheManager();
    final ContentCatalogCacheCoordinator generations=new ContentCatalogCacheCoordinator(caches);
    final MutableClock clock=new MutableClock();
    final CachedContentCatalogReader reader=new CachedContentCatalogReader(source,caches,generations,clock);
    final Filter filter=new Filter(ContentType.NEWS,null,null,null);
    final PracticeContentPageData<PracticeContentSummaryData> page=PracticeContentPageData.of(List.of(
            new PracticeContentSummaryData(1L,ContentType.NEWS,"Title","NEWS",Difficulty.BEGINNER,30,"Script")),0,10,1);

    @AfterEach void cleanup(){TransactionSynchronizationManager.clear();}

    @Test void identicalListsAndFacetsReuseTheLoadedValueUntilTtl() {
        when(source.listSnapshot(filter,0,10)).thenReturn(new ContentCatalogSnapshot<>(page,null));
        var facets=new Facets(ContentType.NEWS,List.of(new Facet("NEWS","News",1,1)),List.of(),"revision");
        when(source.facetsSnapshot(ContentType.NEWS)).thenReturn(new ContentCatalogSnapshot<>(facets,null));
        assertThat(reader.list(filter,0,10)).isEqualTo(page);
        assertThat(reader.list(filter,0,10)).isEqualTo(page);
        assertThat(reader.facets(ContentType.NEWS)).isEqualTo(facets);
        assertThat(reader.facets(ContentType.NEWS)).isEqualTo(facets);
        verify(source,times(1)).listSnapshot(filter,0,10);verify(source,times(1)).facetsSnapshot(ContentType.NEWS);
        clock.now=clock.now.plus(CachedContentCatalogReader.TTL);
        reader.list(filter,0,10);verify(source,times(2)).listSnapshot(filter,0,10);
    }

    @Test void scheduledPublicationExpiresBothResultsAtTheBoundary() {
        var next=OffsetDateTime.ofInstant(clock.instant().plusSeconds(20),ZoneOffset.UTC);
        when(source.listSnapshot(filter,0,10)).thenReturn(new ContentCatalogSnapshot<>(page,next));
        var facets=new Facets(ContentType.NEWS,List.of(),List.of(),"r");
        when(source.facetsSnapshot(ContentType.NEWS)).thenReturn(new ContentCatalogSnapshot<>(facets,next));
        reader.list(filter,0,10);reader.facets(ContentType.NEWS);
        clock.now=next.toInstant().minusNanos(1);reader.list(filter,0,10);reader.facets(ContentType.NEWS);
        verify(source,times(1)).listSnapshot(filter,0,10);verify(source,times(1)).facetsSnapshot(ContentType.NEWS);
        clock.now=next.toInstant();reader.list(filter,0,10);reader.facets(ContentType.NEWS);
        verify(source,times(2)).listSnapshot(filter,0,10);verify(source,times(2)).facetsSnapshot(ContentType.NEWS);
    }

    @Test void keysSeparateEveryFilterAndPaginationIncludingLiteralNullSentinels() {
        String base=ContentCatalogCacheKeys.list(filter,0,10);
        assertThat(base).isEqualTo(ContentCatalogCacheKeys.list(new Filter(ContentType.NEWS," ",null,null),0,10));
        assertThat(List.of(
                ContentCatalogCacheKeys.list(new Filter(ContentType.ANNOUNCER,null,null,null),0,10),
                ContentCatalogCacheKeys.list(new Filter(ContentType.NEWS,"none",null,null),0,10),
                ContentCatalogCacheKeys.list(new Filter(ContentType.NEWS,"a:b",null,null),0,10),
                ContentCatalogCacheKeys.list(new Filter(ContentType.NEWS,null,Difficulty.BEGINNER,null),0,10),
                ContentCatalogCacheKeys.list(new Filter(ContentType.NEWS,null,null,LearningFocus.BOTH),0,10),
                ContentCatalogCacheKeys.list(filter,1,10),ContentCatalogCacheKeys.list(filter,0,20)))
                .doesNotContain(base).doesNotHaveDuplicates();
    }

    @Test void concurrentSameKeyMissesLoadTheDatabaseOnlyOnce() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(source.listSnapshot(filter,0,10)).thenAnswer(invocation->{entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return new ContentCatalogSnapshot<>(page,null);});
        try(var pool=Executors.newFixedThreadPool(8)) {
            var futures=new java.util.ArrayList<Future<?>>();
            for(int i=0;i<8;i++)futures.add(pool.submit(()->reader.list(filter,0,10)));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();release.countDown();
            for(var future:futures)assertThat(future.get(5,TimeUnit.SECONDS)).isEqualTo(page);
        } finally {release.countDown();}
        verify(source,times(1)).listSnapshot(filter,0,10);
    }

    @Test void invalidationDuringLoadMakesTheLateFillUnreachableAcrossInstances() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(source.listSnapshot(filter,0,10)).thenAnswer(invocation->{entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return new ContentCatalogSnapshot<>(page,null);});
        try(var pool=Executors.newSingleThreadExecutor()) {
            var old=pool.submit(()->reader.list(filter,0,10));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            new ContentCatalogCacheCoordinator(caches).invalidate();release.countDown();old.get(5,TimeUnit.SECONDS);
        } finally {release.countDown();}
        var changed=PracticeContentPageData.<PracticeContentSummaryData>of(List.of(),0,10,0);
        when(source.listSnapshot(filter,0,10)).thenReturn(new ContentCatalogSnapshot<>(changed,null));
        var another=new CachedContentCatalogReader(source,caches,new ContentCatalogCacheCoordinator(caches),clock);
        assertThat(another.list(filter,0,10)).isEqualTo(changed);
        verify(source,times(2)).listSnapshot(filter,0,10);
    }

    @Test void writesAndRepeatableReadTransactionsBypassSharedCache() {
        when(source.listSnapshot(filter,0,10)).thenReturn(new ContentCatalogSnapshot<>(page,null));
        reader.list(filter,0,10);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        reader.list(filter,0,10);reader.list(filter,0,10);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
        reader.list(filter,0,10);
        verify(source,times(4)).listSnapshot(filter,0,10);
    }

    @Test void cacheReadAndWriteFailuresFallBackButDatabaseFailureIsNeverRetriedOrHidden() {
        CacheManager broken=mock(CacheManager.class);when(broken.getCache(anyString())).thenThrow(new IllegalStateException("cache down"));
        var fallback=new CachedContentCatalogReader(source,broken,new ContentCatalogCacheCoordinator(broken),clock);
        when(source.listSnapshot(filter,0,10)).thenReturn(new ContentCatalogSnapshot<>(page,null));
        assertThat(fallback.list(filter,0,10)).isEqualTo(page);
        var failure=new IllegalStateException("database down");when(source.listSnapshot(filter,0,10)).thenThrow(failure);
        assertThatThrownBy(()->fallback.list(filter,0,10)).isSameAs(failure);
        verify(source,times(2)).listSnapshot(filter,0,10);
        Cache brokenEntry=mock(Cache.class);when(brokenEntry.get(any(),eq(CachedContentCatalogReader.Entry.class))).thenThrow(new IllegalStateException("read"));
        doThrow(new IllegalStateException("write")).when(brokenEntry).put(any(),any());
        CacheManager partial=mock(CacheManager.class);
        when(partial.getCache(PracticeContentCacheNames.CATALOG_GENERATION)).thenReturn(caches.getCache(PracticeContentCacheNames.CATALOG_GENERATION));
        when(partial.getCache(PracticeContentCacheNames.LIST)).thenReturn(brokenEntry);
        var degraded=new CachedContentCatalogReader(source,partial,new ContentCatalogCacheCoordinator(partial),clock);
        doReturn(new ContentCatalogSnapshot<>(page,null)).when(source).listSnapshot(filter,0,10);
        assertThat(degraded.list(filter,0,10)).isEqualTo(page);
        verify(source,times(3)).listSnapshot(filter,0,10);
    }

    @Test void failedInvalidationRotatesTheGenerationBeforeServingRecoveredCache() {
        var down=new AtomicBoolean(false);
        CacheManager flaky=new CacheManager(){
            public Cache getCache(String name){if(down.get())throw new IllegalStateException("offline");return caches.getCache(name);}
            public java.util.Collection<String> getCacheNames(){return caches.getCacheNames();}
        };
        var coordinator=new ContentCatalogCacheCoordinator(flaky);String before=coordinator.generation();
        down.set(true);coordinator.invalidate();
        assertThatThrownBy(coordinator::generation).isInstanceOf(IllegalStateException.class);
        down.set(false);assertThat(coordinator.generation()).isNotEqualTo(before);
    }

    static class MutableClock extends Clock {
        Instant now=Instant.parse("2030-01-01T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
}
