package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.training.domain.entity.VoiceRecording;
import org.example.voice.training.domain.model.AnalysisRequestData;
import org.example.voice.training.domain.model.AnalysisRetryData;
import org.example.voice.training.domain.port.TrainingAnalysisWriter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class TrainingAnalysisWriterImpl implements TrainingAnalysisWriter {

    private final VoiceRecordingJpaRepository voiceRecordingJpaRepository;
    private final AnalysisResultJpaRepository analysisResultJpaRepository;

    @Override
    @Transactional
    public AnalysisRequestData createPending(Long recordingId) {
        // 분석 요청 row를 먼저 저장한 뒤 analysisId를 큐 payload로 사용한다.
        // native returning 대신 JPA save 결과에서 생성 id를 얻는다.
        VoiceRecording recording = voiceRecordingJpaRepository.findById(recordingId)
                .orElseThrow(() -> new BaseException(ErrorCode.RECORDING_NOT_FOUND));
        AnalysisResult analysisResult = analysisResultJpaRepository.save(AnalysisResult.pending(recording));
        return new AnalysisRequestData(
                analysisResult.getId(),
                analysisResult.getStatus(),
                analysisResult.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public AnalysisRetryData retry(Long previousAnalysisId, Long recordingId, Integer retryCount) {
        // 재시도는 기존 실패 row를 새 PENDING 작업처럼 다시 큐에 태우는 방식이다.
        // retryCount 컬럼이 현재 스키마에 없어서 응답값은 Reader가 센 실패 횟수로 계산해 전달한다.
        AnalysisResult analysisResult = analysisResultJpaRepository.findById(previousAnalysisId)
                .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_NOT_FOUND));
        analysisResult.retry();
        return new AnalysisRetryData(
                analysisResult.getId(),
                analysisResult.getStatus(),
                retryCount,
                analysisResult.updatedAt()
        );
    }
}
