package org.example.voice.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.auth.domain.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BCryptPasswordHasher implements PasswordHasher {
    private final PasswordEncoder passwordEncoder;
    public String hash(String rawPassword) { return passwordEncoder.encode(rawPassword); }
    public boolean matches(String rawPassword, String encodedPassword) {
        return encodedPassword != null && passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
