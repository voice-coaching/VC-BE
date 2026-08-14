package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import java.util.Optional;

public interface AnalysisResultReader {
    Optional<AnalysisResult> findOwned(Long analysisId, Long userId);
    Optional<AnalysisResult> findOwnedForUpdate(Long analysisId, Long userId);
    Optional<AnalysisResult> findLatestBySession(Long sessionId, Long userId);
}
