package org.example.voice.analysis.infrastructure.canonical;

import org.example.voice.analysis.infrastructure.runpod.RunPodAnalysisProperties;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandoffPeerReadinessTest {
    private final RunPodContract contract = new RunPodContract();

    private Map<String, String> audioDigests() {
        var map = new LinkedHashMap<String, String>();
        CanonicalHandoffReadiness.SCHEMAS.forEach(name -> map.put(name, contract.schemaSha256(name)));
        return map;
    }

    private Map<String, String> videoDigests() {
        var map = new LinkedHashMap<String, String>();
        CanonicalAudiovisualReadiness.SCHEMAS.forEach(name -> map.put(name, contract.schemaSha256(name)));
        return map;
    }

    @Test
    void onlyCurrentV2WithCompletePinsIsAccepted() {
        var audio = audioDigests();
        var video = videoDigests();
        assertThat(HandoffPeerReadiness.matches(response("v1", audio, true), audio)).isFalse();
        var combined = new LinkedHashMap<>(audio);
        combined.putAll(video);
        assertThat(HandoffPeerReadiness.matches(response("v2", combined, true), audio)).isTrue();
        assertThat(HandoffPeerReadiness.matches(response("v1", combined, true), audio)).isFalse();
    }

    @Test
    void incompatibleVersionsMissingAudioPinsAndUnknownExtensionsStayClosed() {
        var audio = audioDigests();
        var video = videoDigests();
        assertThat(HandoffPeerReadiness.matches(response("v3", audio, true), audio)).isFalse();
        assertThat(HandoffPeerReadiness.matches(response("v2", audio, false), audio)).isFalse();
        var missing = new LinkedHashMap<>(audio);
        missing.remove("runpod_result_v6.schema.json");
        assertThat(HandoffPeerReadiness.matches(response("v2", missing, true), audio)).isFalse();
        var changed = new LinkedHashMap<>(audio);
        changed.put("runpod_result_v6.schema.json", "0".repeat(64));
        assertThat(HandoffPeerReadiness.matches(response("v2", changed, true), audio)).isFalse();
        changed = new LinkedHashMap<>(audio);
        changed.put("unknown.schema.json", "0".repeat(64));
        assertThat(HandoffPeerReadiness.matches(response("v2", changed, true), audio)).isFalse();
        changed = new LinkedHashMap<>(audio);
        changed.put("runpod_result_v6.schema.json", "0".repeat(64));
        assertThat(HandoffPeerReadiness.matches(response("v2", changed, true), audio)).isFalse();
    }

    private com.fasterxml.jackson.databind.JsonNode response(String version, Map<String, String> digests, boolean configured) {
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of(
                "contractVersion", "voice-coaching.canonical-handoff." + version,
                "executorConfigured", configured, "schemaDigests", digests));
    }

    @Test
    void aV2PeerWithExactCurrentSchemasOpensAudioAdmission() throws Exception {
        var settings = mock(CanonicalHandoffSettings.class);
        var evidence = mock(CanonicalEvidenceSettings.class);
        var verifier = mock(CanonicalSemanticVerifier.class);
        var worker = mock(CanonicalHandoffWorker.class);
        var archive = mock(CanonicalArchiveWorker.class);
        var writer = mock(CanonicalArchiveWriter.class);
        var effects = mock(CanonicalResultEffectsWorker.class);
        var delivery = mock(CanonicalDeliverySpool.class);
        var deliveryWorker = mock(CanonicalDeliveryWorker.class);
        var pod = new RunPodAnalysisProperties();
        pod.setEndpointUrl("https://worker.example");
        pod.setApiToken("fixture-token");
        when(settings.workerEnabled()).thenReturn(true);
        when(settings.admissionEnabled()).thenReturn(true);
        when(settings.archiveEnabled()).thenReturn(true);
        when(settings.budget()).thenReturn(536870912L);
        when(evidence.journalEnabled()).thenReturn(true);
        when(evidence.callbackEnabled()).thenReturn(true);
        when(worker.operational()).thenReturn(true);
        when(archive.operational()).thenReturn(true);
        when(writer.configured()).thenReturn(true);
        when(effects.operational()).thenReturn(true);
        when(delivery.enabled()).thenReturn(true);
        when(delivery.operational()).thenReturn(true);
        when(delivery.headroom(CanonicalHandoffSettings.AUDIOVISUAL_RESERVATION)).thenReturn(true);
        when(deliveryWorker.operational()).thenReturn(true);
        var hashes = audioDigests();
        hashes.putAll(videoDigests());
        byte[] bytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(Map.of(
                "contractVersion", "voice-coaching.canonical-handoff.v2",
                "executorConfigured", true, "admissionEnabled", true, "schemaDigests", hashes));
        var http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<java.io.InputStream> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (name, value) -> true));
        when(reply.body()).thenReturn(new ByteArrayInputStream(bytes));
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(reply);
        var readiness = new CanonicalHandoffReadiness(settings, evidence, verifier, worker, archive,
                writer, effects, contract, pod, delivery, deliveryWorker);
        ReflectionTestUtils.setField(readiness, "http", http);
        try {
            ReflectionTestUtils.invokeMethod(readiness, "probe");
            assertThat(readiness.admission()).isTrue();
        } finally {
            readiness.close();
        }
    }
}
