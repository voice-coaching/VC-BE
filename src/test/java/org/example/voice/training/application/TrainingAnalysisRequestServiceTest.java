package org.example.voice.training.application;

import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationGrant;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationIssue;
import org.example.voice.analysis.domain.port.AnalysisAuthorizationIssuer;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.model.AnalysisRequestData;
import org.example.voice.training.domain.model.AnalysisConsentData;
import org.example.voice.training.domain.model.SelectedRecordingAnalysisData;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.example.voice.training.domain.port.TrainingAnalysisReader;
import org.example.voice.training.domain.port.TrainingAnalysisWriter;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.port.VoiceRecordingReader;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingAnalysisRequestServiceTest {

    @Mock private TrainingSessionService trainingSessionService;
    @Mock private VoiceRecordingReader voiceRecordingReader;
    @Mock private TrainingAnalysisReader trainingAnalysisReader;
    @Mock private TrainingAnalysisWriter trainingAnalysisWriter;
    @Mock private TrainingSessionWriter trainingSessionWriter;
    @Mock private AnalysisJobPublisher analysisJobPublisher;
    @Mock private AnalysisAuthorizationIssuer analysisAuthorizationIssuer;

    @Test
    void createsTrustedWorkerRequestAndPersistsItsEventGeneration() {
        SelectedRecordingAnalysisData source = new SelectedRecordingAnalysisData(
                50L,
                12L,
                "2026-09-02T00:00:00Z",
                "안녕하세요. 오늘 날씨가 좋습니다.",
                "recordings/50.wav",
                "audio/wav",
                1234L,
                1200,
                "d".repeat(64),
                LearningFocus.PRONUNCIATION,
                RecordingQualityStatus.PASS
        );
        when(voiceRecordingReader.findSelectedForAnalysis(7L, 9L)).thenReturn(Optional.of(source));
        when(trainingAnalysisWriter.createPending(eq(50L), any())).thenReturn(
                new AnalysisRequestData(35L, AnalysisStatus.PENDING, OffsetDateTime.now())
        );

        allowAuthorization();
        service().requestAnalysis(7L, 9L, consent());

        ArgumentCaptor<AnalysisWorkerRequest> request = ArgumentCaptor.forClass(AnalysisWorkerRequest.class);
        verify(analysisJobPublisher).publish(request.capture());
        verify(trainingSessionWriter).updateStatus(7L, TrainingSessionStatus.ANALYZING);
        verify(trainingAnalysisWriter).createPending(eq(50L), eq(request.getValue().eventId()));
        assertThat(request.getValue().analysisId()).isEqualTo(35L);
        assertThat(request.getValue().contentId()).isEqualTo(12L);
        assertThat(request.getValue().audioObjectKey()).isEqualTo("recordings/50.wav");
        assertThat(request.getValue().scriptSha256()).hasSize(64);
        assertThat(request.getValue().schemaVersion()).isEqualTo("voice-coaching.analysis-request.v3");
        assertThat(request.getValue().audioSha256()).isEqualTo("d".repeat(64));
        assertThat(request.getValue().authorizationGrant().requestEventId())
                .isEqualTo(request.getValue().eventId());
    }

    @Test
    void rejectsLegacyUrlRatherThanPublishingItToTheWorker() {
        SelectedRecordingAnalysisData source = new SelectedRecordingAnalysisData(
                50L,
                12L,
                "2026-09-02T00:00:00Z",
                "안녕하세요. 오늘 날씨가 좋습니다.",
                "https://storage.example.com/legacy.wav",
                "audio/wav",
                1234L,
                1200,
                "d".repeat(64),
                LearningFocus.PRONUNCIATION,
                RecordingQualityStatus.PASS
        );
        when(voiceRecordingReader.findSelectedForAnalysis(7L, 9L)).thenReturn(Optional.of(source));
        when(trainingAnalysisWriter.createPending(eq(50L), any())).thenReturn(
                new AnalysisRequestData(35L, AnalysisStatus.PENDING, OffsetDateTime.now())
        );

        allowAuthorization();
        assertThatThrownBy(() -> service().requestAnalysis(7L, 9L, consent()))
                .isInstanceOfSatisfying(BaseException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ANALYSIS_SOURCE_NOT_READY));

        verify(analysisJobPublisher, never()).publish(any());
    }

    @Test
    void rejectsMissingExplicitConsentBeforeCreatingAnalysisState() {
        assertThatThrownBy(() -> service().requestAnalysis(
                7L, 9L, new AnalysisConsentData(false, "voice-analysis-consent-v1")
        )).isInstanceOfSatisfying(BaseException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ANALYSIS_CONSENT_REQUIRED));

        verify(trainingAnalysisWriter, never()).createPending(any(), any());
        verify(analysisJobPublisher, never()).publish(any());
    }

    private TrainingAnalysisRequestService service() {
        return new TrainingAnalysisRequestService(
                trainingSessionService,
                voiceRecordingReader,
                trainingAnalysisReader,
                trainingAnalysisWriter,
                trainingSessionWriter,
                analysisJobPublisher,
                analysisAuthorizationIssuer
        );
    }

    private void allowAuthorization() {
        when(analysisAuthorizationIssuer.issue(any())).thenAnswer(invocation -> {
            AnalysisAuthorizationIssue issue = invocation.getArgument(0);
            Instant issuedAt = Instant.parse("2026-09-02T00:00:00Z");
            return new AnalysisAuthorizationGrant(
                    AnalysisAuthorizationGrant.GRANT_VERSION,
                    "test-key-v1",
                    issue.requestEventId(),
                    issue.analysisId(),
                    issue.contentId(),
                    issue.promptRevision(),
                    issue.scriptSha256(),
                    sha256(issue.audioObjectKey()),
                    issue.audioSha256(),
                    issue.mimeType(),
                    issue.fileSizeBytes(),
                    issue.durationMs(),
                    issue.learningFocus(),
                    "b".repeat(64),
                    issue.consentPolicyRevision(),
                    issuedAt,
                    issuedAt.plusSeconds(300),
                    AnalysisAuthorizationGrant.PURPOSE,
                    AnalysisAuthorizationGrant.DATA_CATEGORY,
                    true,
                    false,
                    "c".repeat(64)
            );
        });
    }

    private static AnalysisConsentData consent() {
        return new AnalysisConsentData(true, "voice-analysis-consent-v1");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
