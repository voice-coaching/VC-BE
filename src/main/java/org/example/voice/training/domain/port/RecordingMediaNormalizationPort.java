package org.example.voice.training.domain.port;

import org.example.voice.training.domain.model.NormalizedRecordingData;

/** Backend-owned media parsing and canonical audio preparation boundary. */
public interface RecordingMediaNormalizationPort {

    NormalizedRecordingData normalize(
            Long userId,
            Long sessionId,
            String sourceObjectKey,
            String declaredMimeType,
            long declaredFileSizeBytes
    );

    void deleteNormalizedObject(Long userId, Long sessionId, String objectKey);
}
