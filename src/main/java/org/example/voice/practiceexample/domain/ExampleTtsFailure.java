package org.example.voice.practiceexample.domain;

public class ExampleTtsFailure extends RuntimeException {
    private final boolean retryable;
    public ExampleTtsFailure(String code, boolean retryable) { super(code); this.retryable = retryable; }
    public boolean retryable() { return retryable; }
}
