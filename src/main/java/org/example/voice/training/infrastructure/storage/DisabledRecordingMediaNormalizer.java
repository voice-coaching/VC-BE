package org.example.voice.training.infrastructure.storage;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.model.NormalizedRecordingData;
import org.example.voice.training.domain.port.RecordingMediaNormalizationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "storage.media-normalization",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DisabledRecordingMediaNormalizer implements RecordingMediaNormalizationPort {

    @Override
    public NormalizedRecordingData normalize(
            Long userId,
            Long sessionId,
            String sourceObjectKey,
            String declaredMimeType,
            long declaredFileSizeBytes
    ) {
        throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }

    @Override
    public void deleteNormalizedObject(Long userId, Long sessionId, String objectKey) {
        throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
    }
}
