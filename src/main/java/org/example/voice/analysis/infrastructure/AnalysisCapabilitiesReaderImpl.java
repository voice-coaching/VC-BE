package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.model.AnalysisCapabilitiesData;
import org.example.voice.analysis.domain.port.AnalysisCapabilitiesReader;
import org.example.voice.analysis.domain.type.AnalysisConfigurationStatus;
import org.example.voice.analysis.infrastructure.authorization.AnalysisAuthorizationProperties;
import org.example.voice.analysis.infrastructure.runpod.RunPodAnalysisProperties;
import org.example.voice.analysis.infrastructure.stream.AnalysisStreamProperties;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.model.RecordingMediaPolicy;
import org.example.voice.training.infrastructure.storage.MediaNormalizationProperties;
import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AnalysisCapabilitiesReaderImpl implements AnalysisCapabilitiesReader {
    private final ObjectStorageProperties storage;
    private final MediaNormalizationProperties normalization;
    private final AnalysisStreamProperties stream;
    private final RunPodAnalysisProperties runPod;
    private final AnalysisAuthorizationProperties authorization;

    @Value("${analysis.transport:disabled}")
    private String analysisTransport;

    @Override
    public AnalysisCapabilitiesData read() {
        boolean uploadConfigured = storage.isEnabled() && normalization.isEnabled();
        String policyRevision = authorization.getConsentPolicyRevision();
        return new AnalysisCapabilitiesData(
                status(uploadConfigured),
                status(uploadConfigured && (stream.isEnabled() || isRunPodHttpConfigured())),
                List.of(LearningFocus.PRONUNCIATION),
                RecordingMediaPolicy.AUDIO_MIME_TYPES,
                RecordingMediaPolicy.VIDEO_MIME_TYPES,
                Math.min(RecordingMediaPolicy.MAXIMUM_AUDIO_BYTES, normalization.getMaximumInputBytes()),
                Math.min(RecordingMediaPolicy.MAXIMUM_VIDEO_BYTES, normalization.getMaximumInputBytes()),
                normalization.getMinimumDurationMs(),
                normalization.getMaximumDurationMs(),
                true,
                true,
                true,
                policyRevision == null || policyRevision.isBlank() ? null : policyRevision,
                RecordingMediaPolicy.VIDEO_CONSENT_POLICY_REVISION
        );
    }

    private static AnalysisConfigurationStatus status(boolean configured) {
        return configured ? AnalysisConfigurationStatus.CONFIGURED : AnalysisConfigurationStatus.NOT_CONFIGURED;
    }

    private boolean isRunPodHttpConfigured() {
        return "runpod_http".equals(analysisTransport) && runPod.isConfigured();
    }
}
