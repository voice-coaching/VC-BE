package org.example.voice.mypage.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class NoOpCacheConfig {

    @Bean
    @Primary
    CacheManager noOpCacheManager() {
        return new NoOpCacheManager();
    }
}
