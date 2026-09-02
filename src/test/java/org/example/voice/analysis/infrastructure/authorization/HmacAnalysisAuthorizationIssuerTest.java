package org.example.voice.analysis.infrastructure.authorization;

import org.example.voice.analysis.domain.model.AnalysisAuthorizationGrant;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationIssue;
import org.example.voice.analysis.domain.model.AnalysisWorkerVisualInput;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacAnalysisAuthorizationIssuerTest {
    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    @Test
    void issuesShortLivedGrantBoundToEveryRequestClaim() {
        HmacAnalysisAuthorizationIssuer issuer = issuer("voice-analysis-consent-v1");

        AnalysisAuthorizationGrant grant = issuer.issue(issue("voice-analysis-consent-v1"));

        assertThat(grant.requestEventId()).isEqualTo(UUID.fromString("4adfe173-0691-4e89-b94e-a5c5c5085826"));
        assertThat(grant.issuedAtUtc()).isEqualTo(NOW);
        assertThat(grant.expiresAtUtc()).isEqualTo(NOW.plusSeconds(300));
        assertThat(grant.consentReceiptSha256()).hasSize(64);
        assertThat(grant.audioObjectKeySha256()).hasSize(64);
        assertThat(grant.audioSha256()).isEqualTo("d".repeat(64));
        assertThat(grant.signature()).hasSize(64);
        assertThat(grant.signature()).isNotEqualTo("0".repeat(64));
        assertThat(grant.signature())
                .isEqualTo("94416ceb8712c06e9b3798867eb7e8a0e13dfb547f965090aae66da50a1dc353");
        assertThat(new String(grant.canonicalSigningInput())).contains("scriptSha256:64:");
    }

    @Test
    void matchesPythonCanonicalHmacVectorWhenVisualClaimsArePresent() {
        HmacAnalysisAuthorizationIssuer issuer = issuer("voice-analysis-consent-v1");
        AnalysisAuthorizationIssue base = issue("voice-analysis-consent-v1");
        AnalysisWorkerVisualInput visualInput = new AnalysisWorkerVisualInput(
                "recordings/analysis-video/00000000-0000-0000-0000-000000000001.mp4",
                "f".repeat(64),
                "video/mp4",
                4567L,
                "e".repeat(64),
                "voice-video-processing-consent-v1"
        );
        AnalysisAuthorizationIssue issue = new AnalysisAuthorizationIssue(
                base.requestEventId(),
                base.analysisId(),
                base.contentId(),
                base.promptRevision(),
                base.scriptSha256(),
                base.audioObjectKey(),
                base.audioSha256(),
                base.mimeType(),
                base.fileSizeBytes(),
                base.durationMs(),
                base.learningFocus(),
                base.consentReceiptSha256(),
                base.consentPolicyRevision(),
                visualInput
        );

        AnalysisAuthorizationGrant grant = issuer.issue(issue);

        assertThat(grant.visualObjectKeySha256())
                .isEqualTo("350294a93744e37eb7c03f54a50710177c99a7231b485eb39061983c95cba7d6");
        assertThat(grant.signature())
                .isEqualTo("dc132de88e2cba1bb2e1a6916d7bfc30525eafb77d8b2b943b3a4232c8906208");
    }

    @Test
    void refusesAStaleClientPolicyRevision() {
        HmacAnalysisAuthorizationIssuer issuer = issuer("voice-analysis-consent-v2");

        assertThatThrownBy(() -> issuer.issue(issue("voice-analysis-consent-v1")))
                .isInstanceOfSatisfying(BaseException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.ANALYSIS_CONSENT_POLICY_MISMATCH));
    }

    @Test
    void rejectsWeakSigningSecretAtCompositionTime() {
        AnalysisAuthorizationProperties properties = properties("voice-analysis-consent-v1");
        properties.setSigningSecretBase64(Base64.getEncoder().encodeToString(new byte[16]));

        assertThatThrownBy(() -> new HmacAnalysisAuthorizationIssuer(
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("analysis_authorization_configuration_invalid");
    }

    private static HmacAnalysisAuthorizationIssuer issuer(String policyRevision) {
        return new HmacAnalysisAuthorizationIssuer(
                properties(policyRevision),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static AnalysisAuthorizationProperties properties(String policyRevision) {
        AnalysisAuthorizationProperties properties = new AnalysisAuthorizationProperties();
        properties.setKeyId("backend-test-v1");
        properties.setSigningSecretBase64(Base64.getEncoder().encodeToString("s".repeat(32).getBytes()));
        properties.setConsentPolicyRevision(policyRevision);
        properties.setGrantTtl(java.time.Duration.ofMinutes(5));
        return properties;
    }

    private static AnalysisAuthorizationIssue issue(String policyRevision) {
        return new AnalysisAuthorizationIssue(
                UUID.fromString("4adfe173-0691-4e89-b94e-a5c5c5085826"),
                35L,
                12L,
                "2026-09-02T00:00:00Z",
                "a".repeat(64),
                "recordings/users/9/sessions/7/attempt.wav",
                "d".repeat(64),
                "audio/wav",
                1234L,
                1200,
                LearningFocus.PRONUNCIATION,
                "4bb06f8e4e3a7715d201d573d0aa423762e55dabd61a2c02278fa56cc6d294e0",
                policyRevision
        );
    }
}
