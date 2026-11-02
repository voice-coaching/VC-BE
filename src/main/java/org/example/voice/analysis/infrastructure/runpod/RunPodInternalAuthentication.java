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

    public void verify(String authorizationHeader) {}
}
