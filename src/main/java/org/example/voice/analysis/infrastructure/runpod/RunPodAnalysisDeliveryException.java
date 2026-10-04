package org.example.voice.analysis.infrastructure.runpod;

class RunPodAnalysisDeliveryException extends RuntimeException {

    private int retryAfterSeconds = 1;
    private final String code;
    private final boolean retryable;

    RunPodAnalysisDeliveryException(String code, boolean retryable, Throwable cause) {
        super(code, cause);
        this.code = code;
        this.retryable = retryable;
    }

    RunPodAnalysisDeliveryException retryAfter(String value) {
        try {
            long seconds;
            try { seconds = Long.parseLong(value); }
            catch (NumberFormatException ignored) {
                seconds = java.time.Duration.between(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC),
                        java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)).getSeconds();
            }
            retryAfterSeconds = (int) Math.max(1, Math.min(300, seconds));
        } catch (RuntimeException ignored) { retryAfterSeconds = 1; }
        return this;
    }

    int retryAfterSeconds() { return retryAfterSeconds; }

    String code() {
        return code;
    }

    boolean isRetryable() {
        return retryable;
    }
}
