package org.example.voice.auth.domain.port;

public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
