package org.example.voice.practicecontent.infrastructure.cache;

import org.example.voice.common.cache.CacheKey;
import org.example.voice.practicecontent.controller.dto.PracticeContentNextConditionDto;
import org.example.voice.practicecontent.controller.dto.PracticeContentQueryConditionDto;

public final class PracticeContentCacheKeys {

    public static String list(PracticeContentQueryConditionDto condition) {
        return CacheKey.join(
                condition.type(),
                condition.category(),
                condition.difficulty(),
                condition.focus(),
                condition.page(),
                condition.size()
        );
    }

    public static String next(PracticeContentNextConditionDto condition) {
        return CacheKey.join(
                condition.type(),
                condition.category(),
                condition.difficulty(),
                condition.excludeId()
        );
    }

    private PracticeContentCacheKeys() {
    }
}
