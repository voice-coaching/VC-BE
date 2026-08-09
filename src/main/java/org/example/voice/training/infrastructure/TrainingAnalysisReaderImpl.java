package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.type.AnalysisStatus;
import org.example.voice.training.domain.model.AnalysisProgressData;
import org.example.voice.training.domain.port.TrainingAnalysisReader;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingAnalysisReaderImpl implements TrainingAnalysisReader {

    private final AnalysisResultJpaRepository analysisResultJpaRepository;

    @Override
    public boolean existsRunningAnalysis(Long recordingId) {
        return analysisResultJpaRepository.existsByRecordingIdAndStatusIn(
                recordingId,
                List.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING)
        );
    }

    @Override
    public Optional<AnalysisProgressData> findLatestBySelectedRecording(Long sessionId, Long userId) {
        return analysisResultJpaRepository
                .findFirstByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        sessionId,
                        userId
                )
                .map(this::toProgressData);
    }

    @Override
    public Optional<AnalysisProgressData> findLatestFailedBySelectedRecording(Long sessionId, Long userId) {
        return analysisResultJpaRepository
                .findFirstByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullAndStatusOrderByCreatedAtDescIdDesc(
                        sessionId,
                        userId,
                        AnalysisStatus.FAILED
                )
                .map(this::toProgressData);
    }

    @Override
    public boolean existsCompletedAnalysisForSelectedRecording(Long sessionId, Long userId) {
        return analysisResultJpaRepository
                .existsByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullAndStatus(
                        sessionId,
                        userId,
                        AnalysisStatus.COMPLETED
                );
    }

    @Override
    public int countFailedAnalysis(Long recordingId) {
        return analysisResultJpaRepository.countByRecordingIdAndStatus(recordingId, AnalysisStatus.FAILED);
    }

    private AnalysisProgressData toProgressData(AnalysisResult analysisResult) {
        AnalysisStatus status = analysisResult.getStatus();
        return new AnalysisProgressData(
                analysisResult.getId(),
                status,
                stage(status),
                progressPercent(status),
                analysisResult.getFailureReason(),
                analysisResult.updatedAt()
        );
    }

    private String stage(AnalysisStatus status) {
        // 현재 DB에는 stage 컬럼이 없으므로 프론트 연동용 임시 stage를 status에서 계산한다.
        // AI worker가 세부 진행 단계를 저장하게 되면 이 계산은 DB 조회값으로 대체하면 된다.
        return switch (status) {
            case PENDING -> "WAITING";
            case PROCESSING -> "PRONUNCIATION_ANALYSIS";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
        };
    }

    private Integer progressPercent(AnalysisStatus status) {
        // progressPercent도 현재는 임시값이다.
        // Redis/AI worker가 진행률을 publish하거나 DB에 저장하면 Reader 구현만 교체하면 된다.
        return switch (status) {
            case PENDING -> 0;
            case PROCESSING -> 70;
            case COMPLETED -> 100;
            case FAILED -> 0;
        };
    }
}
