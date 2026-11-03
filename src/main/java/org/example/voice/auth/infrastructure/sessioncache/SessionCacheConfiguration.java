package org.example.voice.auth.infrastructure.sessioncache;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.example.voice.auth.domain.port.LoginSessionCache;
import org.example.voice.auth.domain.model.LoginSessionState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties({SessionCacheProperties.class, DataRedisProperties.class})
public class SessionCacheConfiguration {
    @Bean
    @ConditionalOnProperty(prefix="auth.session-cache", name="enabled", havingValue="false")
    LoginSessionCache disabledSessionCache() {
        return new LoginSessionCache() {
            public boolean enabled() { return false; }
            public Lookup lookup(Long userId) { return Lookup.bypass(); }
            public void publish(Long userId,String ticket,Optional<LoginSessionState> state) {}
            public String beginChange(Long userId) { return null; }
            public void finishChange(Long userId,String changeId) {}
        };
    }

    @Bean(destroyMethod="close")
    @ConditionalOnProperty(prefix="auth.session-cache", name="enabled", havingValue="true", matchIfMissing=true)
    SessionRedisClient sessionRedisClient(DataRedisProperties redis, SessionCacheProperties settings) {
        // This factory is owned by this client, not exposed as a RedisConnectionFactory bean.
        // Spring Cache and AI Stream client selection/configuration therefore remain independent.

        var server = new RedisStandaloneConfiguration(redis.getHost(),redis.getPort());
        server.setDatabase(redis.getDatabase());
        boolean tls=redis.getSsl().isEnabled();
        String user=redis.getUsername(), password=redis.getPassword();
        if (redis.getUrl()!=null) {
            URI url;
            try {url=URI.create(redis.getUrl());}
            catch(IllegalArgumentException invalid) {throw new IllegalArgumentException("Invalid auth session Redis URL");}

            server.setHostName(url.getHost());server.setPort(url.getPort()<0?6379:url.getPort());tls=tls || "rediss".equals(url.getScheme());
            if(url.getPath()!=null && !url.getPath().isBlank() && !"/".equals(url.getPath())) server.setDatabase(Integer.parseInt(url.getPath().substring(1)));
            if (url.getRawUserInfo()!=null) {
                String[] pair=url.getRawUserInfo().split(":",2);
                if(pair.length==2) {user=decode(pair[0]);password=decode(pair[1]);} else {password=decode(pair[0]);}
            }
        }
        if (user!=null && !user.isBlank()) server.setUsername(user);
        if (password!=null) server.setPassword(password);
        var client=LettuceClientConfiguration.builder().commandTimeout(settings.getCommandTimeout())
                .shutdownTimeout(java.time.Duration.ofMillis(100)).clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(settings.getConnectTimeout()).build()).build());
        if(tls)client.useSsl();
        var factory=new LettuceConnectionFactory(server,client.build());
        factory.afterPropertiesSet();factory.start();
        return new SessionRedisClient(factory);
    }

    private static String decode(String text) { return URLDecoder.decode(text.replace("+","%2B"),StandardCharsets.UTF_8); }

    @Bean
    @ConditionalOnProperty(prefix="auth.session-cache", name="enabled", havingValue="true", matchIfMissing=true)
    LoginSessionCache redisSessionCache(SessionRedisClient client,SessionCacheBootstrap bootstrap,SessionCacheProperties properties) {
        return new RedisLoginSessionCache(client.template,bootstrap,properties);
    }

    public static final class SessionRedisClient implements AutoCloseable {
        private final LettuceConnectionFactory factory;
        private final StringRedisTemplate template;
        SessionRedisClient(LettuceConnectionFactory factory) {this.factory=factory;this.template=new StringRedisTemplate(factory);}
        @Override public void close() {factory.destroy();}
    }
}
