package org.example.voice.practicecontent.infrastructure;

import org.example.voice.practicecontent.domain.model.ContentCatalogData.*;
import org.example.voice.practicecontent.domain.model.PracticeContentPageData;
import org.example.voice.practicecontent.domain.model.PracticeContentSummaryData;
import org.example.voice.practicecontent.domain.port.ContentCatalogReader;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.practicecontent.infrastructure.cache.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/** Cache only public discovery data; mutable/private session reads use other ports. */
@Primary
@Repository
public class CachedContentCatalogReader implements ContentCatalogReader {
    public static final Duration TTL = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(CachedContentCatalogReader.class);
    private final ContentCatalogPersistence source;
    private final CacheManager caches;
    private final ContentCatalogCacheCoordinator generations;
    private final Clock clock;
    private final Object[] stripes = new Object[128];

    public CachedContentCatalogReader(ContentCatalogPersistence source, CacheManager caches,
                                     ContentCatalogCacheCoordinator generations, Clock clock) {
        this.source = source;
        this.caches = caches;
        this.generations = generations;
        this.clock = clock;
        for (int i = 0; i < stripes.length; i++) stripes[i] = new Object();
    }

    public record Entry(Object value, Instant validUntil) {}

    @Override
    public PracticeContentPageData<PracticeContentSummaryData> list(Filter filter, int page, int size) {
        return cached(PracticeContentCacheNames.LIST, ContentCatalogCacheKeys.list(filter, page, size),
                PracticeContentPageData.class, () -> source.listSnapshot(filter, page, size));
    }

    @Override
    public Facets facets(ContentType type) {
        return cached(PracticeContentCacheNames.FACETS, "catalog-v1:" + type, Facets.class,
                () -> source.facetsSnapshot(type));
    }

    @Override
    public Adjacent adjacent(Long contentId, Filter filter) {
        return source.adjacent(contentId, filter);
    }

    private <T> T cached(String name, String key, Class<?> expected, Supplier<ContentCatalogSnapshot<T>> loader) {
        if (transactionMustBypass()) return loader.get().value();
        // Bounded local single-flight; independent keys may load concurrently.
        Object stripe = stripes[Math.floorMod(Objects.hash(name, key), stripes.length)];
        synchronized (stripe) {
            String generation;
            Cache cache;
            try {
                generation = generations.generation();
                cache = caches.getCache(name);
            } catch (RuntimeException unavailable) {
                log.warn("catalog_cache_read_unavailable cache={}", name);
                return loader.get().value();
            }
            if (cache == null) return loader.get().value();
            String fullKey = generation + ':' + key;
            try {
                Entry hit = cache.get(fullKey, Entry.class);
                if (hit != null && hit.validUntil() != null && clock.instant().isBefore(hit.validUntil())
                        && expected.isInstance(hit.value())) {
                    @SuppressWarnings("unchecked") T value = (T) hit.value();
                    return value;
                }
            } catch (RuntimeException unavailable) {
                log.warn("catalog_cache_read_unavailable cache={}", name);
            }
            // Database failures are deliberately outside cache-error handling.
            ContentCatalogSnapshot<T> loaded = loader.get();
            Instant now = clock.instant();
            Instant until = now.plus(TTL);
            if (loaded.nextPublicationAt() != null && loaded.nextPublicationAt().toInstant().isBefore(until))
                until = loaded.nextPublicationAt().toInstant();
            try {
                if (now.isBefore(until) && generation.equals(generations.generation()))
                    cache.put(fullKey, new Entry(loaded.value(), until));
            } catch (RuntimeException unavailable) {
                log.warn("catalog_cache_write_unavailable cache={}", name);
            }
            return loaded.value();
        }
    }

    private boolean transactionMustBypass() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return false;
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        return !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || isolation != null && isolation >= Connection.TRANSACTION_REPEATABLE_READ;
    }
}
