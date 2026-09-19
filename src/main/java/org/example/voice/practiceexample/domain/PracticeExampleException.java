package org.example.voice.practiceexample.domain;

public class PracticeExampleException extends RuntimeException {
    private final int status;
    private final String code;
    public PracticeExampleException(int status, String code) { super(code); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
    public static PracticeExampleException missing() { return new PracticeExampleException(404, "RESOURCE_NOT_FOUND"); }
    public static PracticeExampleException invalid() { return new PracticeExampleException(400, "VALIDATION_ERROR"); }
    public static PracticeExampleException unavailable() { return new PracticeExampleException(503, "TTS_UNAVAILABLE"); }
}
