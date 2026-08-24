package org.example.voice.course.infrastructure.cache;

import org.example.voice.common.cache.CacheTtlProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class CourseCacheTtlProvider implements CacheTtlProvider {

    @Override
    public Map<String, Duration> cacheTtls() {
        return Map.of(
                CourseCacheNames.LIST, Duration.ofMinutes(10),
                CourseCacheNames.DETAIL, Duration.ofMinutes(20),
                CourseCacheNames.STEPS, Duration.ofMinutes(20)
        );
    }
}
