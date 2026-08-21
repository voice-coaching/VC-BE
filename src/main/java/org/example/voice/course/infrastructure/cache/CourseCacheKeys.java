package org.example.voice.course.infrastructure.cache;

import org.example.voice.common.cache.CacheKey;
import org.example.voice.course.controller.dto.CourseSearchConditionDto;

public final class CourseCacheKeys {

    public static String list(CourseSearchConditionDto condition, Long userId) {
        return CacheKey.join(
                userId,
                condition.type(),
                condition.difficulty(),
                condition.status(),
                condition.page(),
                condition.size()
        );
    }

    public static String userCourse(Long userId, Long courseId) {
        return CacheKey.join(userId, courseId);
    }

    private CourseCacheKeys() {
    }
}
