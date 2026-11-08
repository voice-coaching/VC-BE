package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.coaching.domain.port.CoachingUsageReservation;
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationIssue;
import org.example.voice.analysis.domain.model.AnalysisAuthorizationGrant;
import org.example.voice.analysis.domain.model.AnalysisClosedBetaContext;
import org.example.voice.analysis.domain.model.AnalysisWorkerVisualInput;
import org.example.voice.analysis.domain.port.AnalysisAuthorizationIssuer;
import org.example.voice.analysis.domain.type.AnalysisExecutionProfile;
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
@RequiredArgsConstructor(onConstructor_=@org.springframework.beans.factory.annotation.Autowired)
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
    private final CoachingUsageReservation coachingUsageService;
    private final org.example.voice.analysis.domain.port.IndependentAnalysisSubmission independent;

    public TrainingAnalysisRequestService(TrainingSessionService sessions,VoiceRecordingReader recordings,
            TrainingAnalysisReader reader,TrainingAnalysisWriter writer,TrainingSessionWriter sessionWriter,
            AnalysisJobPublisher publisher,AnalysisAuthorizationIssuer issuer,ProcessingConsentLedger consents,
            AnalysisAdmissionGuard guard,CoachingUsageReservation usage) {
        this(sessions,recordings,reader,writer,sessionWriter,publisher,issuer,consents,guard,usage,null);
    }

    @Transactional
    public AnalysisRequestData requestAnalysis(Long sessionId, Long userId, AnalysisConsentData consent) {
        return requestAnalysis(sessionId, userId, consent, AnalysisExecutionProfile.LEGACY);
    }

    @Transactional
    public AnalysisRequestData requestAnalysis(Long sessionId, Long userId, AnalysisConsentData consent,
                                              AnalysisExecutionProfile profile) {
        java.util.Objects.requireNonNull(profile, "profile");
        if(profile==AnalysisExecutionProfile.INDEPENDENT_PULL) independent.lockOwner(userId);
        // 선택된 세션의 녹음으로 요청을 구성한다. 별도의 접수·동의·품질 제한은 적용하지 않는다.

        trainingSessionWriter.lockAnalysisSelection(sessionId, userId);
        if(profile==AnalysisExecutionProfile.INDEPENDENT_PULL) independent.assertSession(sessionId,userId);
        SelectedRecordingAnalysisData source = voiceRecordingReader.findSelectedForAnalysis(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.SELECTED_RECORDING_NOT_FOUND));
        if(trainingAnalysisReader.existsRunningAnalysis(source.recordingId()))
            throw new BaseException(ErrorCode.ANALYSIS_ALREADY_RUNNING);

        if (trainingAnalysisReader.existsAnalysis(source.recordingId()))
            throw new BaseException(ErrorCode.ANALYSIS_ALREADY_REQUESTED);

        if (profile != AnalysisExecutionProfile.LEGACY
                && source.learningFocus() != LearningFocus.PRONUNCIATION) {
            throw new BaseException(ErrorCode.ANALYSIS_FOCUS_NOT_SUPPORTED);
        }

        UUID requestEventId = UUID.randomUUID();

        trainingSessionWriter.startAnalysis(sessionId);
        AnalysisRequestData result = trainingAnalysisWriter.createPending(source.recordingId(), requestEventId);

        publish(toWorkerRequest(
                result.analysisId(), requestEventId, userId, sessionId, source,
                null, null
        ), profile, userId);
        return result;
    }

    public AnalysisProgressData getStatus(Long sessionId, Long userId) {
        // 현재 스키마에는 stage/progress 컬럼이 없어서 Reader에서 status 기반 임시 진행값을 계산한다.
        // AI 작업자가 진행률을 DB에 저장하게 되면 Reader 구현만 바꾸면 된다.

        return trainingAnalysisReader.findLatestBySelectedRecording(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_NOT_FOUND));
    }

    @Transactional
    public AnalysisRetryData retry(Long sessionId, Long userId, AnalysisConsentData consent) {
        return retry(sessionId, userId, consent, AnalysisExecutionProfile.LEGACY);
    }

    @Transactional
    public AnalysisRetryData retry(Long sessionId, Long userId, AnalysisConsentData consent,
                                   AnalysisExecutionProfile profile) {
        java.util.Objects.requireNonNull(profile, "profile");
        if(profile==AnalysisExecutionProfile.INDEPENDENT_PULL) independent.lockOwner(userId);
        // 재시도 상태·횟수 제한 없이 현재 분석을 다시 요청한다.
        // 실패 row를 새 request event id의 PENDING 상태로 전환하고 outbox에 다시 기록한다.

        trainingSessionWriter.lockAnalysisSelection(sessionId, userId);
        if(profile==AnalysisExecutionProfile.INDEPENDENT_PULL) independent.assertSession(sessionId,userId);
        SelectedRecordingAnalysisData source = voiceRecordingReader.findSelectedForAnalysis(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        if(profile==AnalysisExecutionProfile.INDEPENDENT_PULL && trainingAnalysisReader.existsRunningAnalysis(source.recordingId()))
            throw new BaseException(ErrorCode.ANALYSIS_ALREADY_RUNNING);

        AnalysisProgressData failed = trainingAnalysisReader.findLatestFailedBySelectedRecording(sessionId, userId)
                .filter(progress -> progress.status() == org.example.voice.analysis.domain.type.AnalysisStatus.FAILED)
                .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_NOT_FOUND));

        if (profile != AnalysisExecutionProfile.LEGACY
                && source.learningFocus() != LearningFocus.PRONUNCIATION) {
            throw new BaseException(ErrorCode.ANALYSIS_FOCUS_NOT_SUPPORTED);
        }

        UUID requestEventId = UUID.randomUUID();

        AnalysisRetryData result = trainingAnalysisWriter.retry(
                failed.analysisId(),
                requestEventId
        );
        if(profile==AnalysisExecutionProfile.INDEPENDENT_PULL) trainingSessionWriter.startAnalysis(sessionId);

        publish(toWorkerRequest(
                result.analysisId(), requestEventId, userId, sessionId, source,
                null, null
        ), profile, userId);
        return result;
    }

    private void publish(AnalysisWorkerRequest request, AnalysisExecutionProfile profile, Long ownerId) {
        if(profile==AnalysisExecutionProfile.INDEPENDENT_PULL) {
            independent.enqueue(request,ownerId);
            return;
        }
        if (profile == AnalysisExecutionProfile.LEGACY) {
            analysisJobPublisher.publish(request);
            return;
        }
        analysisJobPublisher.publish(request, profile);
    }

    private AnalysisWorkerRequest toWorkerRequest(
            Long analysisId,
            UUID requestEventId,
            Long userId,
            Long sessionId,
            SelectedRecordingAnalysisData source,
            String consentPolicyRevision,
            String consentReceiptSha256
    ) {
        try {
            String scriptSha256 = sha256(source.scriptText());
            AnalysisWorkerVisualInput visualInput = source.visualObjectKey() == null
                    ? null
                    : new AnalysisWorkerVisualInput(
                            source.visualObjectKey(),
                            source.visualSha256(),
                            source.visualMimeType(),
                            source.visualFileSizeBytes(),
                            source.visualConsentReceiptSha256(),
                            source.visualConsentPolicyRevision()
                    );
            AnalysisClosedBetaContext closedBetaContext = analysisAuthorizationIssuer.requiresClosedBetaContext()
                    ? new AnalysisClosedBetaContext(
                            AnalysisClosedBetaContext.SCHEMA_VERSION,
                            userId,
                            sessionId,
                            source.recordingId()
                    )
                    : null;
            AnalysisAuthorizationGrant grant = null;
            return new AnalysisWorkerRequest(
                    closedBetaContext == null
                            ? AnalysisWorkerRequest.LEGACY_SCHEMA_VERSION
                            : AnalysisWorkerRequest.SCHEMA_VERSION,
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
                    closedBetaContext,
                    visualInput,
                    grant
            );
        } catch (IllegalArgumentException error) {
            throw new BaseException(ErrorCode.ANALYSIS_SOURCE_NOT_READY);
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
