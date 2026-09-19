package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.domain.model.AnalysisCapabilitiesData;

import java.util.List;

public record AnalysisCapabilitiesResponseDto(
        String recordingUpload,
        String analysisRequests,
        List<String> supportedLearningFocuses,
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
    public static AnalysisCapabilitiesResponseDto from(AnalysisCapabilitiesData data) {
        return new AnalysisCapabilitiesResponseDto(
                data.recordingUpload().name(),
                data.analysisRequests().name(),
                data.supportedLearningFocuses().stream().map(Enum::name).toList(),
                data.acceptedAudioMimeTypes(),
                data.acceptedVideoMimeTypes(),
                data.maximumAudioUploadBytes(),
                data.maximumVideoUploadBytes(),
                data.minimumDurationMs(),
                data.maximumDurationMs(),
                data.videoRequiresAudioTrack(),
                data.voiceProcessingConsentRequired(),
                data.videoProcessingConsentRequired(),
                data.consentPolicyRevision(),
                data.videoProcessingConsentPolicyRevision()
        );
    }
}
