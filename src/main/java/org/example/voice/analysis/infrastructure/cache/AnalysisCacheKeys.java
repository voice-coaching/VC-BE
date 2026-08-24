package org.example.voice.analysis.infrastructure.cache;

import org.example.voice.common.cache.CacheKey;

public final class AnalysisCacheKeys {

    public static String owned(Long userId, Long analysisId) {
        return CacheKey.join(userId, analysisId);
    }

    public static String session(Long userId, Long sessionId) {
        return CacheKey.join(userId, sessionId);
    }

    public static String segments(Long userId, Long analysisId, int page, int size) {
        return CacheKey.join(userId, analysisId, page, size);
    }

    private AnalysisCacheKeys() {
    }
}
