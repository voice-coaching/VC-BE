package org.example.voice.analysis.domain.model;

import org.example.voice.analysis.domain.type.AnalysisConfigurationStatus;
import org.example.voice.practicecontent.domain.type.LearningFocus;

import java.util.List;

public record AnalysisCapabilitiesData(
        AnalysisConfigurationStatus recordingUpload,
        AnalysisConfigurationStatus analysisRequests,
        List<LearningFocus> supportedLearningFocuses,
        List<String> acceptedAudioMimeTypes,
        List<String> acceptedVideoMimeTypes,
        long maximumAudioUploadBytes,
        long maximumVideoUploadBytes,
        int minimumDurationMs,
        int maximumDurationMs,
        boolean videoRequiresAudioTrack,
        boolean voiceProcessingConsentRequired,
        boolean videoProcessingConsentRequired,
        String consentPolicyRevision,
        String videoProcessingConsentPolicyRevision
) {
    public AnalysisCapabilitiesData {
        supportedLearningFocuses = List.copyOf(supportedLearningFocuses);
        acceptedAudioMimeTypes = List.copyOf(acceptedAudioMimeTypes);
        acceptedVideoMimeTypes = List.copyOf(acceptedVideoMimeTypes);
    }
}
