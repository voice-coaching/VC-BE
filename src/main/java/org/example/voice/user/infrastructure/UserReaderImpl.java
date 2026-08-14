package org.example.voice.user.infrastructure;

import org.example.voice.user.domain.port.UserReader;
import lombok.RequiredArgsConstructor;
import org.example.voice.user.domain.entity.User;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserReaderImpl implements UserReader {
    private final UserJpaRepository userJpaRepository;

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByNicknameExcludingUserId(String nickname, Long userId) {
        return userJpaRepository.existsByNicknameIgnoreCaseAndIdNot(nickname, userId);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmailIgnoreCase(email);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByIdForUpdate(Long id) {
        return userJpaRepository.findByIdForUpdate(id);
    }
}
