package org.example.voice.practiceexample.domain.port;

import org.example.voice.practiceexample.domain.model.ExampleData.*;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface ExampleAudioCache {
    Optional<Audio> cached(String key);
    Claim claim(String key, Long userId, OffsetDateTime now);
    void complete(String key, String lease, Audio audio);
    void fail(String key, String lease, OffsetDateTime now);
}
