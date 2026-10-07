package org.example.voice.training.domain.port;

import org.example.voice.training.domain.model.MediaPreparationData;
import java.util.Optional;

/** Private preparation evidence, never a public recording response. */
public interface RecordingMediaPreparationStore {
    void register(long recordingId, MediaPreparationData preparation);
    Optional<MediaPreparationData> find(long recordingId);
}
