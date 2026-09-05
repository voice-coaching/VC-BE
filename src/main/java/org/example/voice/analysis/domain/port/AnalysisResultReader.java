package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisResultData;
import java.util.Optional;

public interface AnalysisResultReader {
    Optional<AnalysisResult> findOwned(Long analysisId, Long userId);
    Optional<AnalysisResult> findOwnedForUpdate(Long analysisId, Long userId);

    Optional<AnalysisResult> findForIngestion(Long analysisId);
    Optional<AnalysisResult> findLatestBySession(Long sessionId, Long userId);
    Optional<AnalysisResultData> findOwnedData(Long analysisId, Long userId);
    Optional<AnalysisResultData> findLatestBySessionData(Long sessionId, Long userId);
}
