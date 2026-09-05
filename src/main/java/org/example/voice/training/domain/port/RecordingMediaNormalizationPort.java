package org.example.voice.training.domain.port;

import org.example.voice.training.domain.model.NormalizedRecordingData;
import org.example.voice.training.domain.model.VisualProcessingAuthorizationData;

/** Backend-owned media parsing and canonical audio preparation boundary. */
public interface RecordingMediaNormalizationPort {

    default NormalizedRecordingData normalize(
            Long userId,
            Long sessionId,
            String sourceObjectKey,
            String declaredMimeType,
            long declaredFileSizeBytes
    ) {
        return normalize(userId, sessionId, sourceObjectKey, declaredMimeType,
                declaredFileSizeBytes, null);
    }

    NormalizedRecordingData normalize(
            Long userId,
            Long sessionId,
            String sourceObjectKey,
            String declaredMimeType,
            long declaredFileSizeBytes,
            VisualProcessingAuthorizationData visualAuthorization
    );

    void deleteNormalizedObject(Long userId, Long sessionId, String objectKey);
}
