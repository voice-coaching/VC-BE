package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.entity.AnalysisSegment;
import org.example.voice.analysis.domain.model.AnalysisWorkerSegment;
import org.example.voice.analysis.domain.port.AnalysisSegmentWriter;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AnalysisSegmentWriterImpl implements AnalysisSegmentWriter {

    private final AnalysisSegmentJpaRepository repository;

    @Override
    public void replaceForAnalysis(AnalysisResult analysisResult, List<AnalysisWorkerSegment> segments) {
        repository.deleteByAnalysisResultId(analysisResult.getId());
        repository.saveAll(segments.stream()
                .map(segment -> AnalysisSegment.from(analysisResult, segment))
                .toList());
    }
}
