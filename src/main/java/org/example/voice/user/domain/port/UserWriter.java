package org.example.voice.user.domain.port;

import org.example.voice.user.domain.entity.User;

public interface UserWriter {
    User save(User user);
}
