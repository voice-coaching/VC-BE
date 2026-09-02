package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.consent.domain.model.ProcessingConsentReceipt;
import org.example.voice.training.controller.dto.RecordingRegisterRequestDto;
import org.example.voice.training.domain.model.RecordingPlaybackUrlData;
import org.example.voice.training.domain.model.NormalizedRecordingData;
import org.example.voice.training.domain.model.RecordingSelectionData;
import org.example.voice.training.domain.model.VoiceRecordingData;
import org.example.voice.training.domain.model.VoiceRecordingRegisteredData;
import org.example.voice.training.domain.model.VisualProcessingAuthorizationData;
import org.example.voice.training.domain.port.VoiceRecordingReader;
import org.example.voice.training.domain.port.VoiceRecordingWriter;
import org.example.voice.training.domain.port.RecordingObjectStoragePort;
import org.example.voice.training.domain.port.RecordingMediaNormalizationPort;
import org.example.voice.training.domain.port.RecordingUploadIntentRegistry;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceRecordingService {

    private static final String VIDEO_PROCESSING_CONSENT_POLICY_REVISION =
            "voice-video-processing-consent-v1";

    // 녹음 파일 메타데이터를 관리한다.
    // 실제 파일 업로드는 프론트가 Presigned URL로 직접 수행하고, 백엔드는 업로드 후 metadata만 등록한다.
    private final VoiceRecordingReader voiceRecordingReader;
    private final VoiceRecordingWriter voiceRecordingWriter;
    private final TrainingSessionService trainingSessionService;
    private final RecordingObjectStoragePort objectStorage;
    private final RecordingMediaNormalizationPort mediaNormalization;
    private final ProcessingConsentLedger processingConsentLedger;
    private final RecordingUploadIntentRegistry uploadIntentRegistry;

    @Transactional
    public VoiceRecordingRegisteredData register(Long sessionId, RecordingRegisterRequestDto request, Long userId) {
        // objectKey는 스토리지에 올라간 파일의 식별자 역할을 한다.
        // 같은 objectKey가 이미 등록되어 있으면 같은 파일을 중복 등록한 것으로 본다.
        trainingSessionService.assertSessionExists(sessionId, userId);
        validateRegisterRequest(request);
        if (voiceRecordingReader.existsByObjectKey(request.objectKey())) {
            throw new BaseException(ErrorCode.RECORDING_ALREADY_REGISTERED);
        }
        validateVideoConsent(request);
        objectStorage.assertUploadedObject(
                userId,
                sessionId,
                request.objectKey(),
                request.mimeType(),
                request.fileSizeBytes()
        );
        VisualProcessingAuthorizationData visualAuthorization = null;
        if (request.mimeType().startsWith("video/")) {
            ProcessingConsentReceipt faceConsent = processingConsentLedger.grantFaceVideoProcessing(
                    userId,
                    sessionId,
                    request.videoProcessingConsentPolicyRevision(),
                    request.objectKey()
            );
            visualAuthorization = new VisualProcessingAuthorizationData(
                    faceConsent.receiptSha256(),
                    request.videoProcessingConsentPolicyRevision()
            );
        }
        NormalizedRecordingData normalized = mediaNormalization.normalize(
                userId,
                sessionId,
                request.objectKey(),
                request.mimeType(),
                request.fileSizeBytes(),
                visualAuthorization
        );
        CanonicalMediaRollbackCleanup rollbackCleanup = new CanonicalMediaRollbackCleanup(
                userId, sessionId, normalized
        );
        registerRollbackCleanup(rollbackCleanup);

        try {
            uploadIntentRegistry.markConsumed(userId, sessionId, request.objectKey());
            return voiceRecordingWriter.register(sessionId, normalized);
        } catch (RuntimeException error) {
            try {
                rollbackCleanup.cleanup();
            } catch (RuntimeException cleanupFailure) {
                cleanupFailure.addSuppressed(error);
                throw cleanupFailure;
            }
            throw error;
        }
    }

    private static void registerRollbackCleanup(CanonicalMediaRollbackCleanup cleanup) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    cleanup.cleanup();
                } catch (RuntimeException ignored) {
                    log.error("Canonical media rollback cleanup requires operator reconciliation");
                }
            }
        });
    }

    public List<VoiceRecordingData> getRecordings(Long sessionId, Long userId) {
        trainingSessionService.assertSessionExists(sessionId, userId);
        return voiceRecordingReader.findBySessionId(sessionId, userId);
    }

    @Transactional
    public RecordingSelectionData select(Long sessionId, Long recordingId, Long userId) {
        // 최종 분석에 사용할 녹음은 음질 검사를 통과한 녹음만 허용한다.
        // 추후 음질검사 AI가 붙으면 PENDING -> PASS/LOW_VOLUME/TOO_NOISY 등의 상태가 자동 갱신된다.
        RecordingQualityStatus qualityStatus = voiceRecordingReader.findQualityStatus(sessionId, recordingId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RECORDING_NOT_FOUND));
        if (qualityStatus != RecordingQualityStatus.PASS) {
            throw new BaseException(ErrorCode.RECORDING_QUALITY_FAILED);
        }
        return voiceRecordingWriter.select(sessionId, recordingId);
    }

    @Transactional
    public void delete(Long sessionId, Long recordingId, Long userId) {
        // 최종 선택된 녹음과 분석 완료된 녹음은 일반 삭제로 지우지 않는다.
        // 학습 기록/분석 결과와 연결된 파일까지 지우는 별도 API가 생길 가능성을 고려한 정책이다.
        voiceRecordingReader.findQualityStatus(sessionId, recordingId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RECORDING_NOT_FOUND));
        if (voiceRecordingReader.isSelected(sessionId, recordingId, userId)) {
            throw new BaseException(ErrorCode.SELECTED_RECORDING_CANNOT_DELETE);
        }
        if (voiceRecordingReader.hasCompletedAnalysis(recordingId)) {
            throw new BaseException(ErrorCode.ANALYZED_RECORDING_CANNOT_DELETE);
        }
        voiceRecordingWriter.delete(sessionId, recordingId);
    }

    public RecordingPlaybackUrlData getPlaybackUrl(Long recordingId, Long userId) {
        return voiceRecordingReader.findPlaybackUrl(recordingId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RECORDING_NOT_FOUND));
    }

    private void validateRegisterRequest(RecordingRegisterRequestDto request) {
        if (request == null
                || request.objectKey() == null
                || request.mimeType() == null
                || request.fileSizeBytes() == null
                || request.fileSizeBytes() <= 0
                || request.durationMs() == null
                || request.durationMs() <= 0) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void validateVideoConsent(RecordingRegisterRequestDto request) {
        if (!request.mimeType().startsWith("video/")) {
            return;
        }
        if (!Boolean.TRUE.equals(request.videoProcessingConsentAccepted())
                || !VIDEO_PROCESSING_CONSENT_POLICY_REVISION.equals(
                        request.videoProcessingConsentPolicyRevision()
                )) {
            throw new BaseException(ErrorCode.VIDEO_PROCESSING_CONSENT_REQUIRED);
        }
    }

    private final class CanonicalMediaRollbackCleanup {
        private final Long userId;
        private final Long sessionId;
        private final NormalizedRecordingData normalized;
        private boolean completed;

        private CanonicalMediaRollbackCleanup(
                Long userId,
                Long sessionId,
                NormalizedRecordingData normalized
        ) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.normalized = normalized;
        }

        private synchronized void cleanup() {
            if (completed) {
                return;
            }
            RuntimeException failure = null;
            try {
                mediaNormalization.deleteNormalizedObject(
                        userId, sessionId, normalized.objectKey()
                );
            } catch (RuntimeException error) {
                failure = error;
            }
            if (normalized.visual() != null) {
                try {
                    mediaNormalization.deleteNormalizedObject(
                            userId, sessionId, normalized.visual().objectKey()
                    );
                } catch (RuntimeException error) {
                    if (failure == null) {
                        failure = error;
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
            completed = true;
        }
    }
}
