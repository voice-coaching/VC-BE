package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CanonicalSemanticVerifierInstallationTest {
    static ObjectNode scoredInstallation() {
        return new ObjectMapper().createObjectNode()
                .put("protocol", "voice-coaching.canonical-offline-verification.v1")
                .put("status", "READY")
                .put("coreManifestSha256", "95f2347ad53ab53030f68a65795cfc5a84ca1c8c41bd03119c9a9fb15d0367c2")
                .put("llmManifestSha256", "1413b196616c919aeaab243fdd57771e70221ffc53a37f9b018d1881499b8c73")
                .put("llmLockSha256", "d8356763579dcfee347073a3f0ff72f4a0f67e33a29fa3b4c71012afcfd4ba01")
                .put("h5ManifestSha256", "9f10296b6944249ded9f5ce2ccfdfa7c53b6e4af670999fe68e9e477e97eaf45")
                .put("h5LockSha256", "93636f0c4befe7f1e34358e74b1077cc94cb2b82064781485b0183c53357d2f5")
                .put("scoredCoreManifestSha256", "08fd8a19b6897d0c3890b0944986685830f24e6e676ea19422038b03a940ba80")
                .put("scoredManifestSha256", "e3d6d6be9d896ede8d75d5f35f5daf4520a8d42f484e5f84a6fc32f8a05fb2e3")
                .put("scoredLockSha256", "dc3db9cc03a3355ddeb5d5819cd6fd46726935c8ca9b830f513e37163c89f4d8");
    }

    @Test void acceptsDeployedScoredVerifierWithoutResidentPackage() {
        assertThatCode(() -> CanonicalSemanticVerifier.validateInstallation(scoredInstallation()))
                .doesNotThrowAnyException();
    }

    @Test void rejectsChangedRequiredPinAndIncompleteResidentPackage() {
        var changed = scoredInstallation().put("scoredLockSha256", "0".repeat(64));
        assertThatThrownBy(() -> CanonicalSemanticVerifier.validateInstallation(changed))
                .isInstanceOf(EvidenceFailure.class);
        var incomplete = scoredInstallation().put("residentCoreManifestSha256",
                "ce16f635da012237b5316b36161d5eeff9432279cd08dbbe7967f5ab7ace7f7e");
        assertThatThrownBy(() -> CanonicalSemanticVerifier.validateInstallation(incomplete))
                .isInstanceOf(EvidenceFailure.class);
    }

    @Test void validatesAllAdvertisedResidentPins() {
        var installed = scoredInstallation()
                .put("residentCoreManifestSha256", "ce16f635da012237b5316b36161d5eeff9432279cd08dbbe7967f5ab7ace7f7e")
                .put("residentManifestSha256", "885a2f0a67494dc3bebed28962222bfe95a0143292fe2de04194b84ea8beadc5")
                .put("residentLockSha256", "1945dcf2fad7fc97621830abed34353c2d372d8224cc9ad11a9c422eabfdf9f4");
        assertThatCode(() -> CanonicalSemanticVerifier.validateInstallation(installed)).doesNotThrowAnyException();
        installed.put("residentLockSha256", "0".repeat(64));
        assertThatThrownBy(() -> CanonicalSemanticVerifier.validateInstallation(installed))
                .isInstanceOf(EvidenceFailure.class);
    }
}
