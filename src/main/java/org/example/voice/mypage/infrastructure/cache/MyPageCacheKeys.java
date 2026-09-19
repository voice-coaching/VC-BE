package org.example.voice.mypage.infrastructure.cache;

import org.example.voice.common.cache.CacheKey;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.example.voice.training.domain.type.TrainingSessionStatus;

import java.time.OffsetDateTime;
import java.util.List;

public final class MyPageCacheKeys {

    public static String history(Long userId, ContentType type, TrainingSessionStatus status,
                                 OffsetDateTime from, OffsetDateTime to, int page, int size) {
        return CacheKey.join(userId, type, status, from, to, page, size);
    }

    public static String historyDetail(Long userId, Long sessionId) {
        return CacheKey.join(userId, sessionId);
    }

    public static String statistics(Long userId, OffsetDateTime from, OffsetDateTime to,
                                    OffsetDateTime todayFrom, OffsetDateTime todayTo) {
        return CacheKey.join(userId, from, to, todayFrom, todayTo);
    }

    public static String unitScores(Long userId, OffsetDateTime from, OffsetDateTime to) {
        return CacheKey.join(userId, from, to);
    }

    public static String scoreTrend(Long userId, String metric, OffsetDateTime from, OffsetDateTime to) {
        return CacheKey.join(userId, metric, from, to);
    }

    public static String recommendations(List<String> targetUnits, ContentType contentType, int limit) {
        return CacheKey.join(targetUnits, contentType, limit);
    }

    private MyPageCacheKeys() {
    }
}
