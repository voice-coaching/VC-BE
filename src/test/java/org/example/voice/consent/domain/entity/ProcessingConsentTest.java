package org.example.voice.consent.domain.entity;

import org.example.voice.consent.domain.type.ProcessingConsentScope;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessingConsentTest {

    private static final UUID EVENT_ID = UUID.fromString("4adfe173-0691-4e89-b94e-a5c5c5085826");
    private static final OffsetDateTime GRANTED_AT = OffsetDateTime.parse("2026-09-03T00:00:00Z");

    @Test
    void acceptsVoiceAndFaceBindingsWithDifferentRequiredSubjects() {
        assertThatCode(() -> ProcessingConsent.grant(
                9L, 7L, 50L, ProcessingConsentScope.VOICE_ANALYSIS,
                "voice-analysis-consent-v1", "a".repeat(64), EVENT_ID,
                "b".repeat(64), GRANTED_AT
        )).doesNotThrowAnyException();
        assertThatCode(() -> ProcessingConsent.grant(
                9L, 7L, null, ProcessingConsentScope.FACE_VIDEO_PROCESSING,
                "voice-video-processing-consent-v1", "c".repeat(64), null,
                "d".repeat(64), GRANTED_AT
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnboundVoiceReceipt() {
        assertThatThrownBy(() -> ProcessingConsent.grant(
                9L, 7L, null, ProcessingConsentScope.VOICE_ANALYSIS,
                "voice-analysis-consent-v1", "a".repeat(64), null,
                "b".repeat(64), GRANTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processing consent scope binding is invalid");
    }

    @Test
    void rejectsInvalidPolicyAndDigestEvidence() {
        assertThatThrownBy(() -> ProcessingConsent.grant(
                9L, 7L, 50L, ProcessingConsentScope.VOICE_ANALYSIS,
                "voice analysis latest", "not-a-digest", EVENT_ID,
                "b".repeat(64), GRANTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processing consent evidence is invalid");
    }

    @Test
    void rejectsNonPositiveOwnershipIdentifiers() {
        assertThatThrownBy(() -> ProcessingConsent.grant(
                0L, 7L, 50L, ProcessingConsentScope.VOICE_ANALYSIS,
                "voice-analysis-consent-v1", "a".repeat(64), EVENT_ID,
                "b".repeat(64), GRANTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processing consent subject is invalid");
    }
}
