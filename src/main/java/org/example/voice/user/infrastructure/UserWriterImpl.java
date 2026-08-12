package org.example.voice.user.infrastructure;

import org.example.voice.user.domain.port.UserWriter;
import lombok.RequiredArgsConstructor;
import org.example.voice.user.domain.entity.User;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserWriterImpl implements UserWriter {
    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.saveAndFlush(user);
    }
}
