package org.example.voice.analysis.infrastructure.cache;

import org.example.voice.common.cache.CacheTtlProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class AnalysisCacheTtlProvider implements CacheTtlProvider {

    @Override
    public Map<String, Duration> cacheTtls() {
        return Map.of(
                AnalysisCacheNames.DETAIL, Duration.ofMinutes(30),
                AnalysisCacheNames.SESSION_RESULT, Duration.ofMinutes(10),
                AnalysisCacheNames.SEGMENTS, Duration.ofMinutes(30)
        );
    }
}
