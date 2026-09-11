package org.example.voice.analysis.infrastructure.runpod;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.runpod")
public class RunPodAnalysisProperties {

    private String endpointUrl;
    private String apiToken;
    private String callbackToken;
    private String callbackBaseUrl;
    private Duration executionTimeout = Duration.ofMinutes(15);
    private Duration claimTtl = Duration.ofSeconds(90);
    private Duration outboxPollInterval = Duration.ofSeconds(1);
    private int batchSize = 25;
    private int dispatchMaxAttempts = 5;
    private int maximumPayloadBytes = 65_536;

    public boolean isConfigured() {
        return hasText(endpointUrl)
                && hasText(apiToken)
                && hasText(callbackToken)
                && hasText(callbackBaseUrl);
    }

    public String normalizedEndpointUrl() {
        if (endpointUrl == null) {
            return "";
        }
        return endpointUrl.endsWith("/")
                ? endpointUrl.substring(0, endpointUrl.length() - 1)
                : endpointUrl;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
