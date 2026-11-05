package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CanonicalAiCoreReleaseTest {
    final RunPodContract contract = new RunPodContract();
    @Test void futureAiCoreReleaseIsAcceptedWithoutBackendAllowlistUpdate() throws Exception {
        var projection = V6AudioFixtures.failure();
        ((ObjectNode) projection.get("sourceIdentity")).put("parentManifestSha256", "f".repeat(64));
        assertThatCode(() -> CanonicalCallbackDocument.parse(CanonicalHandoffDocument.bytes(projection), contract))
                .doesNotThrowAnyException();
    }
    @Test void malformedCoreDigestIsStillRejected() throws Exception {
        var projection = V6AudioFixtures.failure();
        ((ObjectNode) projection.get("sourceIdentity")).put("parentManifestSha256", "not-a-sha256");
        assertThatThrownBy(() -> CanonicalCallbackDocument.parse(CanonicalHandoffDocument.bytes(projection), contract))
                .isInstanceOf(RunPodContractException.class);
    }
}
