package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisSegment;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.domain.port.AnalysisSegmentReader;
import org.example.voice.analysis.exception.AnalysisNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisSegmentService {
    private final AnalysisResultReader analysisResultReader;
    private final AnalysisSegmentReader analysisSegmentReader;

    @Transactional(readOnly = true)
    public SegmentPage getSegments(Long analysisId, Long userId, int page, int size) {
        analysisResultReader.findOwned(analysisId, userId).orElseThrow(AnalysisNotFoundException::new);
        return new SegmentPage(analysisSegmentReader.findPage(analysisId, page, size), page, size, analysisSegmentReader.count(analysisId));
    }

    public record SegmentPage(List<AnalysisSegment> items, int page, int size, long totalElements) {}
}
