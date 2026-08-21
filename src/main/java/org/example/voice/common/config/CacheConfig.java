package org.example.voice.common.config;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.cache.CacheTtlProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final List<CacheTtlProvider> cacheTtlProviders;

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return baseConfiguration(Duration.ofMinutes(10));
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = cacheTtlProviders.stream()
                .flatMap(provider -> provider.cacheTtls().entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> baseConfiguration(entry.getValue())
                ));
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration())
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private RedisCacheConfiguration baseConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        GenericJacksonJsonRedisSerializer.builder().build()
                ))
                .entryTtl(ttl);
    }
}
