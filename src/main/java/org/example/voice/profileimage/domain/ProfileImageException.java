package org.example.voice.profileimage.domain;

public class ProfileImageException extends RuntimeException {
    private final int status;
    private final String code;
    public ProfileImageException(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static ProfileImageException invalid() { return new ProfileImageException(400, "INVALID_PROFILE_IMAGE"); }
    public static ProfileImageException unavailable() { return new ProfileImageException(503, "TEMPORARY_UNAVAILABLE"); }
}
