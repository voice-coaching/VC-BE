package org.example.voice.analysis.infrastructure.runpod;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
import org.example.voice.analysis.infrastructure.canonical.CanonicalAudiovisualReadiness;
import org.example.voice.analysis.infrastructure.canonical.CanonicalHandoffReadiness;
import org.example.voice.analysis.domain.port.AnalysisSubmissionAdmission;
import org.example.voice.training.application.TrainingAnalysisSchemaAdmissionService;
import org.example.voice.training.exception.AnalysisSchemaAdmissionException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnifiedV6AudioContractTest {
    final RunPodContract contract=new RunPodContract();
    final ObjectMapper mapper=new ObjectMapper();

    ObjectNode request() throws Exception {
        var node=(ObjectNode)mapper.readTree(RunPodContractTest.payload(OffsetDateTime.now().plusMinutes(5)));
        node.put("schemaVersion",RunPodContract.REQUEST_V4);
        node.put("resultSchemaVersion",RunPodContract.RESULT_V6);
        node.put("analysisProfile",RunPodContract.AUDIOVISUAL_PROFILE);
        node.putNull("mediaPreparation");
        return node;
    }

    @Test void audioHasExplicitNullMediaAndAnImmutableRoundTrip() throws Exception {
        var codec=new RunPodAnalysisPayloadCodec();
        String original=request().toString();
        var typed=codec.decodeRequest(original);
        String wire=codec.encodeRequest(typed);
        assertThat(wire).contains("\"video\":null","\"mediaPreparation\":null");
        assertThat(contract.digest(wire)).isEqualTo(contract.digest(original));
        assertThat(typed.resultSchemaVersion()).isEqualTo(RunPodContract.RESULT_V6);
    }

    @Test void missingMediaDiscriminatorsAndWrongTupleAreRejected() throws Exception {
        var node=request();node.remove("mediaPreparation");
        assertThatThrownBy(()->contract.parse(node.toString().getBytes(StandardCharsets.UTF_8),"analysisRequest"))
                .isInstanceOf(RunPodContractException.class);
        var wrong=request().put("resultSchemaVersion",RunPodContract.RESULT_V5);
        assertThatThrownBy(()->contract.parse(wrong.toString().getBytes(StandardCharsets.UTF_8),"analysisRequest"))
                .isInstanceOf(RunPodContractException.class);
    }

    @Test void audioDoesNotRequireOptionalVideoReadinessAndExplicitRetiredSchemaIsRejected() {
        var handoff=mock(CanonicalHandoffReadiness.class);
        var video=mock(CanonicalAudiovisualReadiness.class);
        var submissions=mock(AnalysisSubmissionAdmission.class);
        when(handoff.admission()).thenReturn(true);
        var admission=new TrainingAnalysisSchemaAdmissionService(submissions,handoff,video);
        assertThat(admission.selectProfile(null)).isEqualTo(AnalysisExecutionProfile.CANONICAL_AUDIOVISUAL);
        assertThat(admission.selectProfile(RunPodContract.RESULT_V6)).isEqualTo(AnalysisExecutionProfile.CANONICAL_AUDIOVISUAL);
        verifyNoInteractions(video);
        assertThatThrownBy(()->admission.selectProfile(RunPodContract.RESULT_V5))
                .isInstanceOf(AnalysisSchemaAdmissionException.class);
        assertThatThrownBy(()->admission.assertAvailable(RunPodContract.RESULT_V6,true))
                .isInstanceOf(AnalysisSchemaAdmissionException.class);
    }

    @Test void packagedPinsMatchBackendTransportContracts() {
        java.util.Map<String,String> pins=java.util.Map.ofEntries(
                java.util.Map.entry("runpod_http_control_v1.schema.json","58b497491b949b4b672caec4febbc4b4f0950b41a460eebb4f3f31f5092fb0b9"),
                java.util.Map.entry("runpod_http_control_v1_2.schema.json","7acd63e1a0ff9aa68bd2d86c2c5ec37827df10b85b186efac08c1d338287e7a4"),
                java.util.Map.entry("runpod_canonical_parent.schema.json","ab349885e19cec853ba03570dd4bf0d14e0f5130552eea4d907e3b61226af211"),
                java.util.Map.entry("runpod_canonical_input.schema.json","afb262429ecd84625ae2a81692f99219cea7e811aeba7b8914f4dac789835433"),
                java.util.Map.entry("runpod_canonical_output.schema.json","5ca5421f8053b51cc9da9dafe06c0dffcd98e85ca24ce24d373e2d2b0351def8"),
                java.util.Map.entry("runpod_canonical_journal_v1.schema.json","798aa2eee85f9196d0519763119f266b14f065899c03faa75599fab5fed2e5f5"),
                java.util.Map.entry("runpod_analysis_request_v4.schema.json","8fde9cf8ee44b5b151bf31205a355c80789fedc6c8d74ba994274bc898c9eec0"),
                // Backend accepts AI-owned CORE identities; this digest identifies our transport schema.
                java.util.Map.entry("runpod_result_v6.schema.json","4bbc76bf4aeb015d7c1d9bebb7fdeb353b8889b64ee11071c16319f8e3fef362"),
                java.util.Map.entry("runpod_canonical_handoff_v2.schema.json","8677401f5edf88258ca72344cde932b0ddbefdd8e35ccc1885970e6ac2632c07"),
                java.util.Map.entry("runpod_media_preparation_v1.schema.json","0a9c91e88d37f2108a77772512db1d23fcdce48b3a3741407a7f27b31c06946f"),
                java.util.Map.entry("runpod_visual_coaching_input_v1.schema.json","63c560fdd1e775cdce21831ad840e63419f12e53ca5e70023d302bd95cdcdbde"));
        assertThat(CanonicalHandoffReadiness.SCHEMAS).containsExactlyInAnyOrderElementsOf(pins.keySet());
        pins.forEach((file,sha)->assertThat(contract.schemaSha256(file)).as(file).isEqualTo(sha));
    }
}
