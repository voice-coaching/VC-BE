package org.example.voice.mypage.infrastructure.cache;

import org.example.voice.common.cache.CacheTtlProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class MyPageCacheTtlProvider implements CacheTtlProvider {

    @Override
    public Map<String, Duration> cacheTtls() {
        return Map.of(
                MyPageCacheNames.HISTORY, Duration.ofMinutes(5),
                MyPageCacheNames.HISTORY_DETAIL, Duration.ofMinutes(10),
                MyPageCacheNames.STATISTICS, Duration.ofMinutes(5),
                MyPageCacheNames.UNIT_SCORES, Duration.ofMinutes(10),
                MyPageCacheNames.SCORE_TREND, Duration.ofMinutes(10),
                MyPageCacheNames.RECOMMENDATIONS, Duration.ofMinutes(10)
        );
    }
}
