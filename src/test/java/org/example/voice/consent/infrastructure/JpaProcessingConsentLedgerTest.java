package org.example.voice.consent.infrastructure;

import org.example.voice.consent.domain.entity.ProcessingConsent;
import org.example.voice.consent.domain.type.ProcessingConsentScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JpaProcessingConsentLedgerTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    @Test
    void persistsAnOpaqueReceiptBoundToTheAnalysisSubject() {
        ProcessingConsentJpaRepository repository = mock(ProcessingConsentJpaRepository.class);
        JpaProcessingConsentLedger ledger = new JpaProcessingConsentLedger(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ZeroSecureRandom()
        );
        UUID eventId = UUID.fromString("4adfe173-0691-4e89-b94e-a5c5c5085826");

        var receipt = ledger.grantVoiceAnalysis(
                9L,
                7L,
                50L,
                eventId,
                "voice-analysis-consent-v1",
                "a".repeat(64)
        );

        ArgumentCaptor<ProcessingConsent> saved = ArgumentCaptor.forClass(ProcessingConsent.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getScope()).isEqualTo(ProcessingConsentScope.VOICE_ANALYSIS);
        assertThat(saved.getValue().getRequestEventId()).isEqualTo(eventId.toString());
        assertThat(saved.getValue().getSubjectSha256()).isEqualTo("a".repeat(64));
        assertThat(saved.getValue().getReceiptSha256()).isEqualTo(receipt.receiptSha256());
        assertThat(receipt.receiptSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void revokesAllActiveReceiptsForTheSession() {
        ProcessingConsentJpaRepository repository = mock(ProcessingConsentJpaRepository.class);
        JpaProcessingConsentLedger ledger = new JpaProcessingConsentLedger(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ZeroSecureRandom()
        );

        ledger.revokeForSession(9L, 7L);

        verify(repository).revokeForSession(9L, 7L, java.time.OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsAnInvalidFaceVideoSubjectBeforePersistence() {
        ProcessingConsentJpaRepository repository = mock(ProcessingConsentJpaRepository.class);
        JpaProcessingConsentLedger ledger = new JpaProcessingConsentLedger(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ZeroSecureRandom()
        );

        assertThatThrownBy(() -> ledger.grantFaceVideoProcessing(
                9L, 7L, "voice-video-processing-consent-v1", " "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processing consent subject is invalid");
    }

    private static final class ZeroSecureRandom extends SecureRandom {
        @Override
        public void nextBytes(byte[] bytes) {
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }
}
