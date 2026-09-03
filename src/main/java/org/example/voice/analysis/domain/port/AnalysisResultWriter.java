package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.entity.AnalysisResult;

import java.util.Optional;

public interface AnalysisResultWriter {

    Optional<AnalysisResult> findByIdForUpdate(Long analysisId);

    AnalysisResult save(AnalysisResult result);
}
