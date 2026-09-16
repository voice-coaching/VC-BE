package org.example.voice.notification.domain;

public class NotificationException extends RuntimeException {
    private final int status;
    private final String code;
    public NotificationException(int status, String code) { super(code); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
    public static NotificationException invalid() { return new NotificationException(400, "VALIDATION_ERROR"); }
    public static NotificationException missing() { return new NotificationException(404, "RESOURCE_NOT_FOUND"); }
}
