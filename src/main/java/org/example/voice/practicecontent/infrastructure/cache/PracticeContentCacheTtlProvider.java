package org.example.voice.practicecontent.infrastructure.cache;

import org.example.voice.common.cache.CacheTtlProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class PracticeContentCacheTtlProvider implements CacheTtlProvider {

    @Override
    public Map<String, Duration> cacheTtls() {
        return Map.of(
                PracticeContentCacheNames.LIST, Duration.ofMinutes(10),
                PracticeContentCacheNames.DETAIL, Duration.ofMinutes(30),
                PracticeContentCacheNames.NEXT, Duration.ofMinutes(10),
                PracticeContentCacheNames.RECOMMENDATIONS, Duration.ofMinutes(10),
                PracticeContentCacheNames.REFERENCE_AUDIO_LIST, Duration.ofMinutes(30),
                PracticeContentCacheNames.EXISTS, Duration.ofMinutes(30)
        );
    }
}
