package org.example.voice.auth.infrastructure.sessioncache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@Getter @Setter
@ConfigurationProperties("auth.session-cache")
public class SessionCacheProperties {
    private boolean enabled = true;
    private String namespace = "voice-coaching:auth:{sessions}:v1";
    private Duration ttl = Duration.ofMinutes(1);
    private Duration loadingTtl = Duration.ofSeconds(5);
    private Duration commandTimeout = Duration.ofMillis(300);
    private Duration connectTimeout = Duration.ofMillis(300);
    private Duration bootstrapTimeout = Duration.ofSeconds(3);
}
