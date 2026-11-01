package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class UnifiedV6HandoffTest {
    final RunPodContract contract=new RunPodContract();
    static byte[] bytes(ObjectNode n){return n.toString().getBytes(StandardCharsets.UTF_8);}

    @Test void precoreAudioFailurePreservesNoVideoAndHasNoFabricatedOriginals() throws Exception {
        var projection=V6AudioFixtures.failure();
        var doc=CanonicalHandoffDocument.parse(bytes(V6AudioFixtures.handoff(projection)),contract);
        assertThat(doc.projection().schemaVersion()).isEqualTo(RunPodContract.RESULT_V6);
        assertThat(doc.projection().overallScore()).isNull();
        assertThat(doc.projection().decision()).isNull();
        assertThat(doc.inline()).isEmpty();
        assertThat(doc.metadata().path("artifacts")).isEmpty();
        assertThat(projection.path("visual").path("status").asText()).isEqualTo("NOT_PROVIDED");
    }
    @Test void realAudioPreparationDigestIsAllowedEvenWhenExecutionFailedBeforeCore() throws Exception {
        var projection=V6AudioFixtures.failure();
        ((ObjectNode)projection.get("mediaBinding")).put("mediaReceiptSha256","d".repeat(64));
        assertThatCode(()->CanonicalHandoffDocument.parse(bytes(V6AudioFixtures.handoff(projection)),contract))
                .doesNotThrowAnyException();
    }
    @Test void inventedVisualScoreAndContradictoryVideoBindingAreRejected() throws Exception {
        var projection=V6AudioFixtures.failure();
        ((ObjectNode)projection.get("visual")).put("score",99);
        assertThatThrownBy(()->CanonicalCallbackDocument.parse(bytes(projection),contract)).isInstanceOf(RunPodContractException.class);
        var wrong=V6AudioFixtures.failure();
        ((ObjectNode)wrong.get("mediaBinding")).put("videoSha256","e".repeat(64));
        assertThatThrownBy(()->CanonicalCallbackDocument.parse(bytes(wrong),contract)).isInstanceOf(RunPodContractException.class);
    }
    @Test void mismatchedHandoffIdentityIsRejectedBeforeStorage() throws Exception {
        var doc=V6AudioFixtures.handoff(V6AudioFixtures.failure());
        var meta=(ObjectNode)doc.get("metadata");meta.put("analysisId",999);
        doc.put("handoffSha256",contract.digest(meta));
        assertThatThrownBy(()->CanonicalHandoffDocument.parse(bytes(doc),contract)).isInstanceOf(RunPodContractException.class);
    }
    @Test void acceptedAudioNeedsOnlyActualAudioOriginalsAndProjectsNotProvidedVideo() throws Exception {
        var projection=V6AudioFixtures.accepted();var doc=V6AudioFixtures.handoff(projection);
        var meta=(ObjectNode)doc.get("metadata");var artifacts=(com.fasterxml.jackson.databind.node.ArrayNode)meta.get("artifacts");
        var inline=(ObjectNode)doc.get("inlineArtifacts");
        var versions=java.util.Map.of("CORE","canonical_ai_predeploy_v1","BRIDGE_RESULT","canonical-llm-expression-result-v1",
                "BINDING","voice-coaching.canonical-binding.v1","ASSOCIATION","voice-coaching.canonical-association.v2");
        byte[] raw="{}".getBytes(StandardCharsets.UTF_8);
        versions.forEach((kind,version)->{
            artifacts.addObject().put("kind",kind).put("schemaVersion",version).put("sha256",CanonicalCallbackDocument.sha256(raw)).put("byteSize",raw.length);
            inline.put(kind,java.util.Base64.getEncoder().encodeToString(raw));
        });
        doc.put("handoffSha256",contract.digest(meta));
        var parsed=CanonicalHandoffDocument.parse(bytes(doc),contract);
        assertThat(parsed.inline()).containsOnlyKeys("CORE","BRIDGE_RESULT","BINDING","ASSOCIATION");
        var view=new CanonicalPublicProjection(contract).project(parsed.projection());
        assertThat(view.visual().status()).isEqualTo("NOT_PROVIDED");
        assertThat(view.visual().correctiveClaimsAllowed()).isFalse();
        String removedKind=artifacts.get(0).path("kind").asText();
        artifacts.remove(0);inline.remove(removedKind);
        doc.put("handoffSha256",contract.digest(meta));
        assertThatThrownBy(()->CanonicalHandoffDocument.parse(bytes(doc),contract)).isInstanceOf(RunPodContractException.class);
    }
}
