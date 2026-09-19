package org.example.voice.analysis.infrastructure;

import org.example.voice.analysis.domain.type.AnalysisConfigurationStatus;
import org.example.voice.analysis.infrastructure.authorization.AnalysisAuthorizationProperties;
import org.example.voice.analysis.infrastructure.runpod.RunPodAnalysisProperties;
import org.example.voice.analysis.infrastructure.stream.AnalysisStreamProperties;
import org.example.voice.training.infrastructure.storage.MediaNormalizationProperties;
import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisCapabilitiesReaderImplTest {
    @Test
    void httpAnalysisRequiresAnAdvertisableConsentPolicy() {
        var storage = new ObjectStorageProperties();
        storage.setEnabled(true);
        var normalization = new MediaNormalizationProperties();
        normalization.setEnabled(true);
        var runpod = new RunPodAnalysisProperties();
        runpod.setEndpointUrl("https://worker.example.com");
        runpod.setApiToken("test-api-token");
        runpod.setCallbackToken("test-callback-token");
        runpod.setCallbackBaseUrl("https://backend.example.com");
        var authorization = new AnalysisAuthorizationProperties();
        var reader = new AnalysisCapabilitiesReaderImpl(storage, normalization,
                new AnalysisStreamProperties(), runpod, authorization);
        ReflectionTestUtils.setField(reader, "analysisTransport", "runpod_http");
        assertThat(reader.read().analysisRequests()).isEqualTo(AnalysisConfigurationStatus.NOT_CONFIGURED);
        authorization.setConsentPolicyRevision(" ");
        assertThat(reader.read().analysisRequests()).isEqualTo(AnalysisConfigurationStatus.NOT_CONFIGURED);
        authorization.setConsentPolicyRevision("voice-analysis-consent-v1");
        assertThat(reader.read().analysisRequests()).isEqualTo(AnalysisConfigurationStatus.CONFIGURED);
        assertThat(reader.read().consentPolicyRevision()).isEqualTo("voice-analysis-consent-v1");
    }
}
