package org.example.voice.analysis.application;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.model.AnalysisSegmentPageData;
import org.example.voice.analysis.domain.port.AnalysisResultReader;
import org.example.voice.analysis.domain.port.AnalysisSegmentReader;
import org.example.voice.analysis.exception.AnalysisNotCompletedException;
import org.example.voice.analysis.exception.AnalysisNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisSegmentService {
    private final AnalysisResultReader analysisResultReader;
    private final AnalysisSegmentReader analysisSegmentReader;

    @Transactional(readOnly = true)
    public AnalysisSegmentPageData getSegments(Long analysisId, Long userId, int page, int size) {
        analysisResultReader.findOwned(analysisId, userId)
                .map(result -> {
                    if (!result.isCompleted()) {
                        throw new AnalysisNotCompletedException();
                    }
                    return result;
                })
                .orElseThrow(AnalysisNotFoundException::new);
        return analysisSegmentReader.findPageData(analysisId, userId, page, size);
    }
}
