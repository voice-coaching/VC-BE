package org.example.voice.training.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.training.domain.model.AnalysisProgressData;
import org.example.voice.training.domain.model.AnalysisRequestData;
import org.example.voice.training.domain.model.AnalysisRetryData;
import org.example.voice.training.domain.port.AnalysisJobPublisher;
import org.example.voice.training.domain.port.TrainingAnalysisReader;
import org.example.voice.training.domain.port.TrainingAnalysisWriter;
import org.example.voice.training.domain.port.TrainingSessionWriter;
import org.example.voice.training.domain.port.VoiceRecordingReader;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.example.voice.training.domain.type.TrainingSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrainingAnalysisRequestService {

    // AI 분석 연동의 중심 서비스다.
    // 현재는 analysis_results 상태만 만들고 mock publisher를 호출하지만,
    // 이후 Redis Queue를 붙일 때도 서비스는 AnalysisJobPublisher 포트만 호출하게 유지한다.
    private static final int MAX_RETRY_COUNT = 3;

    private final TrainingSessionService trainingSessionService;
    private final VoiceRecordingReader voiceRecordingReader;
    private final TrainingAnalysisReader trainingAnalysisReader;
    private final TrainingAnalysisWriter trainingAnalysisWriter;
    private final TrainingSessionWriter trainingSessionWriter;
    private final AnalysisJobPublisher analysisJobPublisher;

    @Transactional
    public AnalysisRequestData requestAnalysis(Long sessionId, Long userId) {
        // 1. 분석 요청은 "사용자 소유 세션 + 최종 선택 녹음 + 통과한 음질"이 모두 만족될 때만 가능하다.
        trainingSessionService.assertSessionExists(sessionId, userId);
        Long recordingId = voiceRecordingReader.findSelectedRecordingId(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.SELECTED_RECORDING_NOT_FOUND));
        RecordingQualityStatus qualityStatus = voiceRecordingReader.findSelectedRecordingQualityStatus(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.SELECTED_RECORDING_NOT_FOUND));
        if (qualityStatus != RecordingQualityStatus.PASS) {
            throw new BaseException(ErrorCode.AUDIO_QUALITY_NOT_ACCEPTABLE);
        }
        if (trainingAnalysisReader.existsRunningAnalysis(recordingId)) {
            throw new BaseException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }

        // 2. 먼저 DB에 PENDING 분석 row를 만든다.
        // Queue 발행 전 DB 기록을 남겨야, 비동기 작업자가 analysisId로 상태를 추적할 수 있다.
        AnalysisRequestData result = trainingAnalysisWriter.createPending(recordingId);
        trainingSessionWriter.updateStatus(sessionId, TrainingSessionStatus.ANALYZING);

        // 3. 지금은 로그만 찍는 mock publisher지만, 나중에는 Redis Queue에
        // analysisId/sessionId/recordingId payload를 넣는 구현체로 교체하면 된다.
        analysisJobPublisher.publish(result.analysisId(), sessionId, recordingId);
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
    public AnalysisRetryData retry(Long sessionId, Long userId) {
        // 재시도는 실패한 분석만 대상으로 한다.
        // 실패 row를 다시 PENDING으로 돌리고 같은 최종 녹음을 Redis Queue에 다시 발행하는 흐름을 의도했다.
        trainingSessionService.assertSessionExists(sessionId, userId);
        Long recordingId = voiceRecordingReader.findSelectedRecordingId(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        AnalysisProgressData failed = trainingAnalysisReader.findLatestFailedBySelectedRecording(sessionId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_NOT_FAILED));

        int retryCount = trainingAnalysisReader.countFailedAnalysis(recordingId);
        if (retryCount >= MAX_RETRY_COUNT) {
            throw new BaseException(ErrorCode.MAX_RETRY_EXCEEDED);
        }

        AnalysisRetryData result = trainingAnalysisWriter.retry(failed.analysisId(), recordingId, retryCount + 1);
        trainingSessionWriter.updateStatus(sessionId, TrainingSessionStatus.ANALYZING);
        analysisJobPublisher.publish(result.analysisId(), sessionId, recordingId);
        return result;
    }
}
