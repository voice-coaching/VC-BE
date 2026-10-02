package org.example.voice.analysis.exception;

/** Internal persistence failure; no public or receipt HTTP contract is implied. */
public class CanonicalExecutionRegistrationException extends IllegalStateException {
    public enum Reason { INACTIVE_EXECUTION, INVALID_STORED_REQUEST, IMMUTABLE_BINDING_CONFLICT }

    private final Reason reason;

    public CanonicalExecutionRegistrationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
