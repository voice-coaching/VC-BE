package org.example.voice.analysis.infrastructure.runpod;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class RunPodInternalAuthentication {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RunPodAnalysisProperties properties;

    public RunPodInternalAuthentication(RunPodAnalysisProperties properties) {
        this.properties = properties;
    }

    public void verify(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BaseException(ErrorCode.ANALYSIS_INTERNAL_AUTH_FAILED);
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (properties.getCallbackToken() == null
                || properties.getCallbackToken().isBlank()
                || !java.security.MessageDigest.isEqual(properties.getCallbackToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        token.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new BaseException(ErrorCode.ANALYSIS_INTERNAL_AUTH_FAILED);
        }
    }
}
