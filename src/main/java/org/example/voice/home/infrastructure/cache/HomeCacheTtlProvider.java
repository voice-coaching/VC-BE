package org.example.voice.home.infrastructure.cache;

import org.example.voice.common.cache.CacheTtlProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class HomeCacheTtlProvider implements CacheTtlProvider {

    @Override
    public Map<String, Duration> cacheTtls() {
        return Map.of(
                HomeCacheNames.TODAY_STATUS, Duration.ofMinutes(3),
                HomeCacheNames.RECOMMENDATIONS, Duration.ofMinutes(10),
                HomeCacheNames.RECENT_TRAINING, Duration.ofMinutes(3),
                HomeCacheNames.COURSE_PROGRESS, Duration.ofMinutes(5)
        );
    }
}
