package org.example.voice.user.domain.port;

import org.example.voice.user.domain.entity.User;
import java.util.Optional;

public interface UserReader {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);
    Optional<User> findByIdForUpdate(Long id);
}
