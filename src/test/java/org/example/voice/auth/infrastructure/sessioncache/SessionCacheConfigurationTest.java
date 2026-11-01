package org.example.voice.auth.infrastructure.sessioncache;

import org.example.voice.auth.domain.port.LoginSessionCache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class SessionCacheConfigurationTest {
    @Test void dedicatedClientPreservesUrlDatabaseCredentialsTlsAndBoundedTimeout() {
        var redis=new DataRedisProperties();redis.setUrl("redis://fixture%2Buser:fixture%3Apass@localhost:6380/4");redis.getSsl().setEnabled(true);
        try(var client=new SessionCacheConfiguration().sessionRedisClient(redis,new SessionCacheProperties())) {
            var factory=(LettuceConnectionFactory)ReflectionTestUtils.getField(client,"factory");
            assertThat(factory).isNotNull();
            var server=factory.getStandaloneConfiguration();
            assertThat(server.getDatabase()).isEqualTo(4);assertThat(server.getUsername()).isEqualTo("fixture+user");
            assertThat(new String(server.getPassword().get())).isEqualTo("fixture:pass");
            assertThat(factory.getClientConfiguration().isUseSsl()).isTrue();
            assertThat(factory.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofMillis(300));
        }
    }
    @Test void disabledModeKeepsTheDatabasePathWithoutRedisChanges() {
        var cache=new SessionCacheConfiguration().disabledSessionCache();
        assertThat(cache.enabled()).isFalse();assertThat(cache.lookup(7L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
        assertThat(cache.beginChange(7L)).isNull();
    }
    @Test void unsupportedDeploymentAndMalformedUrlFailWithoutExposingConfiguration() {
        var redis=new DataRedisProperties();redis.setCluster(new DataRedisProperties.Cluster());
        assertThatThrownBy(()->new SessionCacheConfiguration().sessionRedisClient(redis,new SessionCacheProperties())).isInstanceOf(IllegalArgumentException.class);
        var invalid=new DataRedisProperties();invalid.setUrl("redis://fixture:private value@localhost");
        assertThatThrownBy(()->new SessionCacheConfiguration().sessionRedisClient(invalid,new SessionCacheProperties()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("private value");
    }
}
