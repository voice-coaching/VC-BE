package org.example.voice.home.infrastructure.cache;

import org.example.voice.common.cache.CacheKey;
import org.example.voice.practicecontent.domain.type.ContentType;

public final class HomeCacheKeys {

    public static String user(Long userId) {
        return CacheKey.join(userId);
    }

    public static String recommendations(Long userId, ContentType type, int limit) {
        return CacheKey.join(userId, type, limit);
    }

    private HomeCacheKeys() {
    }
}
