package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.entity.AnalysisSegment;
import org.example.voice.analysis.domain.model.AnalysisResultStreamData;
import org.example.voice.analysis.domain.port.AnalysisSegmentWriter;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AnalysisSegmentWriterImpl implements AnalysisSegmentWriter {

    private final AnalysisSegmentJpaRepository repository;

    @Override
    public void replaceSegments(AnalysisResult analysisResult, List<AnalysisResultStreamData.Segment> segments) {
        repository.deleteByAnalysisResultId(analysisResult.getId());
        if (segments == null || segments.isEmpty()) {
            return;
        }
        repository.saveAll(segments.stream()
                .map(segment -> AnalysisSegment.create(
                        analysisResult,
                        segment.sequenceNo(),
                        segment.expectedText(),
                        segment.recognizedText(),
                        segment.startMs(),
                        segment.endMs(),
                        segment.matchType(),
                        segment.resultStatus(),
                        segment.targetUnit(),
                        segment.errorType(),
                        segment.pronunciationScore(),
                        segment.intonationScore(),
                        segment.feedback()
                ))
                .toList());
    }
}
