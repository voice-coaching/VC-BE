package org.example.voice.analysis.infrastructure.stream;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * Analysis messages use a dedicated Redis endpoint instead of the Spring Cache
 * endpoint. Cache maintenance must never delete pending Stream messages.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analysis.stream", name = "enabled", havingValue = "true")
public class AnalysisStreamRedisConfiguration {

    private final AnalysisStreamProperties properties;

    @Bean("analysisStreamConnectionFactory")
    public RedisConnectionFactory analysisStreamConnectionFactory() {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                properties.getRedisHost(),
                properties.getRedisPort()
        );
        if (StringUtils.hasText(properties.getRedisUsername())) {
            standalone.setUsername(properties.getRedisUsername());
        }
        if (StringUtils.hasText(properties.getRedisPassword())) {
            standalone.setPassword(properties.getRedisPassword());
        }
        LettuceClientConfiguration client = properties.isRedisSslEnabled()
                ? LettuceClientConfiguration.builder().useSsl().build()
                : LettuceClientConfiguration.defaultConfiguration();
        return new LettuceConnectionFactory(standalone, client);
    }

    @Bean("analysisStreamRedisTemplate")
    public StringRedisTemplate analysisStreamRedisTemplate(
            @Qualifier("analysisStreamConnectionFactory") RedisConnectionFactory connectionFactory
    ) {
        return new StringRedisTemplate(connectionFactory);
    }
}
