package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.consent.domain.port.ProcessingConsentLedger;
import org.example.voice.training.domain.port.RecordingUploadIntentRegistry;
import org.example.voice.training.controller.dto.TrainingSessionCreateRequestDto;
import org.example.voice.training.domain.model.TrainingSessionCancellationData;
import org.example.voice.training.domain.model.TrainingSessionCompletionData;
import org.example.voice.training.domain.model.TrainingSessionCreatedData;
import org.example.voice.training.domain.model.TrainingSessionDetailData;
import org.example.voice.training.domain.port.TrainingSessionReader;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.port.TrainingAnalysisReader;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrainingSessionService {

    // 학습 세션의 생명주기를 담당한다.
    // 세션 생성/조회/완료/취소만 관리하고, 녹음/분석 세부 로직은 각 서비스에 맡긴다.
    private final TrainingSessionReader trainingSessionReader;
    private final TrainingSessionWriter trainingSessionWriter;
    private final TrainingAnalysisReader trainingAnalysisReader;
    private final ProcessingConsentLedger processingConsentLedger;
    private final RecordingUploadIntentRegistry uploadIntentRegistry;

    @Transactional
    public TrainingSessionCreatedData create(TrainingSessionCreateRequestDto request, Long userId) {
        // 콘텐츠가 없으면 404, 존재하지만 게시 상태가 아니면 409로 분리한다.
        // 프론트가 "없는 콘텐츠"와 "현재 학습 불가 콘텐츠"를 다르게 처리할 수 있게 하기 위함이다.
        validateCreateRequest(request);
        if (!trainingSessionReader.existsContent(request.contentId())) {
            throw new BaseException(ErrorCode.CONTENT_NOT_FOUND);
        }
        if (!trainingSessionReader.existsAvailableContent(request.contentId())) {
            throw new BaseException(ErrorCode.CONTENT_NOT_AVAILABLE);
        }
        return trainingSessionWriter.create(
                userId,
                request.contentId(),
                request.courseStepId(),
                request.learningFocus()
        );
    }

    public TrainingSessionDetailData getSession(Long sessionId, Long userId) {
        return trainingSessionReader.findSessionDetail(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
    }

    @Transactional
    public TrainingSessionCompletionData complete(Long sessionId, Integer totalLearningSeconds, Long userId) {
        // 현재 명세에서는 분석이 완료된 뒤에만 학습 완료가 가능하다.
        // AI 연동 전 테스트에서는 analysis_results.status를 COMPLETED로 직접 바꿔야 이 조건을 통과한다.
        assertSessionExists(sessionId, userId);
        if (!trainingAnalysisReader.existsCompletedAnalysisForSelectedRecording(sessionId, userId)) {
            throw new BaseException(ErrorCode.ANALYSIS_NOT_COMPLETED);
        }
        return trainingSessionWriter.complete(sessionId, totalLearningSeconds);
    }

    @Transactional
    public TrainingSessionCancellationData cancel(Long sessionId, Long userId) {
        // 완료/취소된 세션은 더 이상 상태를 되돌리지 않는다.
        // 취소 시 Writer에서 최종 선택되지 않은 임시 녹음을 삭제 대상으로 표시한다.
        TrainingSessionStatus status = trainingSessionReader.findSessionStatus(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        if (status == TrainingSessionStatus.COMPLETED || status == TrainingSessionStatus.CANCELED) {
            throw new BaseException(ErrorCode.SESSION_ALREADY_FINISHED);
        }
        TrainingSessionCancellationData canceled = trainingSessionWriter.cancel(sessionId);
        processingConsentLedger.revokeForSession(userId, sessionId);
        uploadIntentRegistry.expireForSession(userId, sessionId);
        return canceled;
    }

    public void assertSessionExists(Long sessionId, Long userId) {
        if (!trainingSessionReader.existsSession(sessionId, userId)) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void validateCreateRequest(TrainingSessionCreateRequestDto request) {
        if (request == null || request.contentId() == null || request.learningFocus() == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
