package org.example.voice.analysis.infrastructure.runpod;

/** A safe, machine-readable internal protocol error. Never include payload data. */
public class RunPodContractException extends RuntimeException {
    private final int status;
    private final String reason;

    public RunPodContractException(int status, String reason) {
        super(reason);
        this.status = status;
        this.reason = reason;
    }

    public int status() { return status; }
    public String reason() { return reason; }
}
