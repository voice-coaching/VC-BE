package org.example.voice.analysis.infrastructure.runpod;

class RunPodAnalysisDeliveryException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    RunPodAnalysisDeliveryException(String code, boolean retryable, Throwable cause) {
        super(code, cause);
        this.code = code;
        this.retryable = retryable;
    }

    String code() {
        return code;
    }

    boolean isRetryable() {
        return retryable;
    }
}
