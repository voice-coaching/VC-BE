package org.example.voice.title.domain;

public class TitleException extends RuntimeException {
    private final int status;
    public TitleException(int status, String code) { super(code); this.status = status; }
    public int status() { return status; }
}
