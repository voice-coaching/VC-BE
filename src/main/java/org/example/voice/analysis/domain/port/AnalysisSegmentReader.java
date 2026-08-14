package org.example.voice.analysis.domain.port;

import org.example.voice.analysis.domain.entity.AnalysisSegment;
import java.util.List;

public interface AnalysisSegmentReader {
    List<AnalysisSegment> findPage(Long analysisId, int page, int size);
    long count(Long analysisId);
}
