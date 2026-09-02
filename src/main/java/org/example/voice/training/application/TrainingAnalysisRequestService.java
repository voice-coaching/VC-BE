package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationIssue;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationGrant;
import org.example.voice.analysis.domain.port.AnalysisAuthorizationIssuer;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.consent.domain.model.ProcessingConsentReceipt;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.model.AnalysisProgressData;
import org.example.voice.training.domain.model.AnalysisRequestData;
import org.example.voice.training.domain.model.AnalysisRetryData;
import org.example.voice.training.domain.model.AnalysisConsentData;
import org.example.voice.training.domain.model.SelectedRecordingAnalysisData;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.example.voice.training.domain.port.AnalysisAdmissionGuard;
import org.example.voice.training.domain.port.TrainingAnalysisReader;
import org.example.voice.training.domain.port.TrainingAnalysisWriter;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.port.VoiceRecordingReader;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingAnalysisRequestService {

    // 분석 요청의 도메인 검증과 transaction 경계를 소유한다. Redis Stream I/O는 publisher adapter에 숨긴다.
    private final TrainingSessionService trainingSessionService;
    private final VoiceRecordingReader voiceRecordingReader;
    private final TrainingAnalysisReader trainingAnalysisReader;
    private final TrainingAnalysisWriter trainingAnalysisWriter;
    private final TrainingSessionWriter trainingSessionWriter;
    private final AnalysisJobPublisher analysisJobPublisher;
    private final AnalysisAuthorizationIssuer analysisAuthorizationIssuer;
    private final ProcessingConsentLedger processingConsentLedger;
    private final AnalysisAdmissionGuard analysisAdmissionGuard;

    @Transactional
    public AnalysisRequestData requestAnalysis(Long sessionId, Long userId, AnalysisConsentData consent) {
        // 1. 분석 요청은 "사용자 소유 세션 + 최종 선택 녹음 + 통과한 음질"이 모두 만족될 때만 가능하다.
        trainingSessionService.assertSessionExists(sessionId, userId);
        analysisAdmissionGuard.acquireAndAssertAvailable(userId);
        validateConsent(consent);
        SelectedRecordingAnalysisData source = voiceRecordingReader.findSelectedForAnalysis(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.SELECTED_RECORDING_NOT_FOUND));
        validateSupportedFocus(source);
        if (source.qualityStatus() != RecordingQualityStatus.PASS) {
            throw new BaseException(ErrorCode.AUDIO_QUALITY_NOT_ACCEPTABLE);
        }
        if (trainingAnalysisReader.existsRunningAnalysis(source.recordingId())) {
            throw new BaseException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }
        if (trainingAnalysisReader.existsAnalysis(source.recordingId())) {
            throw new BaseException(ErrorCode.ANALYSIS_ALREADY_REQUESTED);
        }

        UUID requestEventId = UUID.randomUUID();
        ProcessingConsentReceipt consentReceipt = processingConsentLedger.grantVoiceAnalysis(
                userId,
                sessionId,
                source.recordingId(),
                requestEventId,
                consent.policyRevision(),
                source.audioSha256()
        );
        trainingSessionWriter.startAnalysis(sessionId);
        AnalysisRequestData result = trainingAnalysisWriter.createPending(source.recordingId(), requestEventId);
        analysisJobPublisher.publish(toWorkerRequest(
                result.analysisId(), requestEventId, source, consent.policyRevision(), consentReceipt.receiptSha256()
        ));
        return result;
    }

    public AnalysisProgressData getStatus(Long sessionId, Long userId) {
        // 현재 스키마에는 stage/progress 컬럼이 없어서 Reader에서 status 기반 임시 진행값을 계산한다.
        // AI 작업자가 진행률을 DB에 저장하게 되면 Reader 구현만 바꾸면 된다.
        trainingSessionService.assertSessionExists(sessionId, userId);
        return trainingAnalysisReader.findLatestBySelectedRecording(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_NOT_FOUND));
    }

    @Transactional
    public AnalysisRetryData retry(Long sessionId, Long userId, AnalysisConsentData consent) {
        // 재시도는 실패한 분석만 대상으로 한다.
        // 실패 row를 새 request event id의 PENDING 상태로 전환하고 outbox에 다시 기록한다.
        trainingSessionService.assertSessionExists(sessionId, userId);
        analysisAdmissionGuard.acquireAndAssertAvailable(userId);
        validateConsent(consent);
        SelectedRecordingAnalysisData source = voiceRecordingReader.findSelectedForAnalysis(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        validateSupportedFocus(source);
        AnalysisProgressData failed = trainingAnalysisReader.findLatestFailedBySelectedRecording(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_NOT_FAILED));

        UUID requestEventId = UUID.randomUUID();
        ProcessingConsentReceipt consentReceipt = processingConsentLedger.grantVoiceAnalysis(
                userId,
                sessionId,
                source.recordingId(),
                requestEventId,
                consent.policyRevision(),
                source.audioSha256()
        );
        trainingSessionWriter.assertAnalysisRetryAllowed(sessionId);
        AnalysisRetryData result = trainingAnalysisWriter.retry(
                failed.analysisId(),
                requestEventId
        );
        analysisJobPublisher.publish(toWorkerRequest(
                result.analysisId(), requestEventId, source, consent.policyRevision(), consentReceipt.receiptSha256()
        ));
        return result;
    }

    private AnalysisWorkerRequest toWorkerRequest(
            Long analysisId,
            UUID requestEventId,
            SelectedRecordingAnalysisData source,
            String consentPolicyRevision,
            String consentReceiptSha256
    ) {
        try {
            String scriptSha256 = sha256(source.scriptText());
            AnalysisAuthorizationGrant grant = analysisAuthorizationIssuer.issue(
                    new AnalysisAuthorizationIssue(
                            requestEventId,
                            analysisId,
                            source.contentId(),
                            source.promptRevision(),
                            scriptSha256,
                            source.audioObjectKey(),
                            source.audioSha256(),
                            source.mimeType(),
                            source.fileSizeBytes(),
                            source.durationMs(),
                            source.learningFocus(),
                            consentReceiptSha256,
                            consentPolicyRevision
                    )
            );
            return new AnalysisWorkerRequest(
                    AnalysisWorkerRequest.SCHEMA_VERSION,
                    requestEventId,
                    analysisId,
                    source.contentId(),
                    source.promptRevision(),
                    source.scriptText(),
                    scriptSha256,
                    source.audioObjectKey(),
                    source.audioSha256(),
                    source.mimeType(),
                    source.fileSizeBytes(),
                    source.durationMs(),
                    source.learningFocus(),
                    grant
            );
        } catch (IllegalArgumentException error) {
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
        }
    }

    private static void validateConsent(AnalysisConsentData consent) {
        if (consent == null
                || !Boolean.TRUE.equals(consent.accepted())
                || consent.policyRevision() == null
                || consent.policyRevision().isBlank()
                || consent.policyRevision().length() > 100) {
            throw new BaseException(ErrorCode.ANALYSIS_CONSENT_REQUIRED);
        }
    }

    private static void validateSupportedFocus(SelectedRecordingAnalysisData source) {
        if (source.learningFocus() != LearningFocus.PRONUNCIATION) {
            throw new BaseException(ErrorCode.ANALYSIS_FOCUS_NOT_SUPPORTED);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
