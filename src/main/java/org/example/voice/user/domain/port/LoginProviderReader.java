package org.example.voice.user.domain.port;

import java.util.List;

public interface LoginProviderReader {
    List<String> findByUserId(Long userId);
}
