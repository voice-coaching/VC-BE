package org.example.voice.analysis.infrastructure.independent;

/** Safe transport classification only: never carry server payloads, URIs or credentials. */
public final class IndependentTransportException extends RuntimeException {
    private final int status;
    private final long retryAfterSeconds;
    public IndependentTransportException(int status,String code,long retryAfterSeconds) {
        super(code);this.status=status;this.retryAfterSeconds=retryAfterSeconds;
    }
    public int status() { return status; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
    public boolean inputRejected() { return java.util.Set.of(400,409,413,415,422).contains(status); }
}
