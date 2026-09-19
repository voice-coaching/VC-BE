package org.example.voice.training.domain.port;

import org.example.voice.training.domain.model.RecordingSelectionData;
import org.example.voice.training.domain.model.NormalizedRecordingData;
import org.example.voice.training.domain.model.VoiceRecordingRegisteredData;

public interface VoiceRecordingWriter {

    VoiceRecordingRegisteredData register(
            Long sessionId,
            NormalizedRecordingData recording
    );

    RecordingSelectionData select(Long sessionId, Long recordingId);

    void delete(Long sessionId, Long recordingId);
}
