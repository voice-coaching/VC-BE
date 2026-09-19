package org.example.voice.analysis.domain.model;

import java.util.List;

public record AnalysisSegmentPageData(
        List<AnalysisSegmentData> items,
        int page,
        int size,
        long totalElements
) {
}
