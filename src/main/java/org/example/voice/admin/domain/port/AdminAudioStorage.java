package org.example.voice.admin.domain.port;

import org.example.voice.admin.domain.model.AdminAudioData.*;

public interface AdminAudioStorage {
    default void assertAvailable() {}
    SignedUpload sign(Upload upload);
    VerifiedAudio verifyAndStore(Upload upload);
    Playback playback(Audio audio);
}
