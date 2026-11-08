package org.example.voice.practicecontent.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** A shared generation makes fills begun before a committed edit unreachable. */
@Component
@RequiredArgsConstructor
public class ContentCatalogCacheCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ContentCatalogCacheCoordinator.class);
    private static final String KEY = "generation-v1";
    private final CacheManager caches;
    private boolean invalidationPending;

    public synchronized String generation() {
        Cache cache = caches.getCache(PracticeContentCacheNames.CATALOG_GENERATION);
        if (cache == null) throw new IllegalStateException("CATALOG_CACHE_UNAVAILABLE");
        if (invalidationPending) return rotate(cache);
        String generation = cache.get(KEY, String.class);
        if (generation == null) {
            String candidate = UUID.randomUUID().toString();
            Cache.ValueWrapper existing = cache.putIfAbsent(KEY, candidate);
            generation = existing == null ? candidate : (String) existing.get();
        }
        if (generation == null) throw new IllegalStateException("CATALOG_CACHE_UNAVAILABLE");
        return generation;
    }

    public synchronized void invalidate() {
        // Keep bypassing old generations after an outage until rotation succeeds.
        invalidationPending = true;
        try {
            Cache cache = caches.getCache(PracticeContentCacheNames.CATALOG_GENERATION);
            if (cache == null) throw new IllegalStateException("CATALOG_CACHE_UNAVAILABLE");
            rotate(cache);
        } catch (RuntimeException failure) {
            log.warn("catalog_cache_invalidation_pending");
        }
    }

    private String rotate(Cache cache) {
        String next = UUID.randomUUID().toString();
        cache.put(KEY, next);
        invalidationPending = false;
        return next;
    }
}
