package org.example.voice.analysis.infrastructure.authorization;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.authorization")
public class AnalysisAuthorizationProperties {
    private String keyId;
    private String signingSecretBase64;
    private String consentPolicyRevision;
    private Duration grantTtl = Duration.ofMinutes(5);
}
