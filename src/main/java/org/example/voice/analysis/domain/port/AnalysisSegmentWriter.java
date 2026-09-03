package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.model.AnalysisResultStreamData;

import java.util.List;

public interface AnalysisSegmentWriter {

    void replaceSegments(AnalysisResult analysisResult, List<AnalysisResultStreamData.Segment> segments);
}
