package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.voice.analysis.infrastructure.runpod.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LipAudiovisualAdmissionTest {
    @Test void publicVideoRemainsClosedBeforeRuntimeVerification() {
        var environment=new StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",Map.of(
                "analysis.canonical.audiovisual.worker-enabled",true,"analysis.canonical.audiovisual.admission-enabled",true)));
        var readiness=new CanonicalAudiovisualReadiness(environment,mock(CanonicalHandoffReadiness.class),
                mock(CanonicalHandoffSettings.class),mock(CanonicalSemanticVerifier.class),new RunPodContract(),
                mock(RunPodAnalysisProperties.class),mock(org.springframework.jdbc.core.JdbcTemplate.class),mock(CanonicalDeliverySpool.class));
        try { assertThat(readiness.supported()).isFalse();assertThat(readiness.admission()).isFalse(); }
        finally { readiness.close(); }
    }
    @Test void schemaDigestsRemainObservationalButExecutorAndAdmissionMustBeReal() throws Exception {
        var response=new ObjectMapper().readTree("""
            {"contractVersion":"voice-coaching.canonical-handoff.v2","executorConfigured":true,
             "admissionEnabled":true,"supportedLearningFocuses":["PRONUNCIATION"],"schemaDigests":{"fixture":"different"}}
            """);
        assertThat(HandoffPeerReadiness.matches(response,Map.of("fixture","expected"))).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode)response).put("executorConfigured",false);
        assertThat(HandoffPeerReadiness.matches(response,Map.of())).isFalse();
    }
}
