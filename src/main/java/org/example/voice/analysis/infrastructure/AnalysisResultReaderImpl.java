package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisResultData;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AnalysisResultReaderImpl implements AnalysisResultReader {

    private final AnalysisResultJpaRepository repository;

    public Optional<AnalysisResult> findOwned(Long analysisId, Long userId) {
        return repository.findByIdAndRecordingTrainingSessionUserId(analysisId, userId);
    }

    public Optional<AnalysisResult> findOwnedForUpdate(Long analysisId, Long userId) {
        return repository.findOwnedForUpdate(analysisId, userId);
    }

    public Optional<AnalysisResult> findLatestBySession(Long sessionId, Long userId) {
        return repository
                .findFirstByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        sessionId,
                        userId
                );
    }

    @Override
    @Cacheable(
            cacheNames = AnalysisCacheNames.DETAIL,
            key = "T(org.example.voice.analysis.infrastructure.cache.AnalysisCacheKeys).owned(#userId, #analysisId)",
            unless = "#result.isEmpty() || !#result.get().isCompleted()"
    )
    public Optional<AnalysisResultData> findOwnedData(Long analysisId, Long userId) {
        return repository.findByIdAndRecordingTrainingSessionUserId(analysisId, userId)
                .map(this::toData);
    }

    @Override
    @Cacheable(
            cacheNames = AnalysisCacheNames.SESSION_RESULT,
            key = "T(org.example.voice.analysis.infrastructure.cache.AnalysisCacheKeys).session(#userId, #sessionId)",
            unless = "#result.isEmpty() || !#result.get().isCompleted()"
    )
    public Optional<AnalysisResultData> findLatestBySessionData(Long sessionId, Long userId) {
        return repository
                .findFirstByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        sessionId,
                        userId
                )
                .map(this::toData);
    }

    private AnalysisResultData toData(AnalysisResult result) {
        return new AnalysisResultData(
                result.getId(),
                result.getStatus(),
                result.getTranscript(),
                result.getSttConfidence(),
                result.getOverallScore(),
                result.getPronunciationScore(),
                result.getIntonationScore(),
                result.getSpeedWpm(),
                result.getSpeedStatus(),
                result.getStressScore(),
                result.getPauseScore(),
                result.getStrengthsText(),
                result.getWeaknessesText(),
                result.getSummaryFeedback(),
                result.getAnalyzedAt()
        );
    }
}
