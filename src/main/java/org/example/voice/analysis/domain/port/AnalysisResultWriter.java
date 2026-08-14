package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.entity.AnalysisResult;

public interface AnalysisResultWriter {
    AnalysisResult save(AnalysisResult result);
}
