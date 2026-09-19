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

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TrainingAnalysisWriterImpl implements TrainingAnalysisWriter {

    private static final int MAX_RETRY_COUNT = 3;

    private final VoiceRecordingJpaRepository voiceRecordingJpaRepository;
    private final AnalysisResultJpaRepository analysisResultJpaRepository;

    @Override
    @Transactional
    public AnalysisRequestData createPending(Long recordingId, UUID requestEventId) {
        // 분석 요청 row를 먼저 저장한 뒤 analysisId를 큐 payload로 사용한다.
        // native returning 대신 JPA save 결과에서 생성 id를 얻는다.
        VoiceRecording recording = voiceRecordingJpaRepository.findById(recordingId)
                .orElseThrow(() -> new BaseException(ErrorCode.RECORDING_NOT_FOUND));
        AnalysisResult analysisResult = analysisResultJpaRepository.save(AnalysisResult.pending(recording, requestEventId));
        return new AnalysisRequestData(
                analysisResult.getId(),
                analysisResult.getStatus(),
                analysisResult.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public AnalysisRetryData retry(Long previousAnalysisId, UUID requestEventId) {
        // 같은 분석 row를 잠근 뒤 영속 retry_count를 증가시켜 동시 재시도와 process 재시작에도 상한을 보존한다.
        AnalysisResult analysisResult = analysisResultJpaRepository.findForIngestion(previousAnalysisId)
                .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_NOT_FOUND));
        if (analysisResult.getStatus() != org.example.voice.analysis.domain.type.AnalysisStatus.FAILED) {
            throw new BaseException(ErrorCode.ANALYSIS_NOT_FAILED);
        }
        if (analysisResult.getRetryCount() >= MAX_RETRY_COUNT) {
            throw new BaseException(ErrorCode.MAX_RETRY_EXCEEDED);
        }
        analysisResult.retry(requestEventId);
        return new AnalysisRetryData(
                analysisResult.getId(),
                analysisResult.getStatus(),
                analysisResult.getRetryCount(),
                analysisResult.updatedAt()
        );
    }
}
