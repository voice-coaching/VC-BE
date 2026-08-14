package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AnalysisResultReaderImpl implements AnalysisResultReader {
    private final AnalysisResultJpaRepository repository;
    public Optional<AnalysisResult> findOwned(Long analysisId, Long userId) { return repository.findByIdAndRecordingTrainingSessionUserId(analysisId, userId); }
    public Optional<AnalysisResult> findOwnedForUpdate(Long analysisId, Long userId) { return repository.findOwnedForUpdate(analysisId, userId); }
    public Optional<AnalysisResult> findLatestBySession(Long sessionId, Long userId) { return repository.findFirstByRecordingTrainingSessionIdAndRecordingTrainingSessionUserIdAndRecordingSelectedTrueAndRecordingDeletedAtIsNullOrderByCreatedAtDescIdDesc(sessionId, userId); }
}
