package org.example.voice.practicecontent.domain.port;

import java.time.OffsetDateTime;

public interface ReferenceAudioAssets {
    Playback playback(String objectKey);

    record Playback(String url, OffsetDateTime expiresAt) {
    }
}
