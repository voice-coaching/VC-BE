package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisWorkerSegment;

import java.util.List;

public interface AnalysisSegmentWriter {

    void replaceForAnalysis(AnalysisResult analysisResult, List<AnalysisWorkerSegment> segments);
}
