package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisSegment;
import org.example.voice.analysis.domain.model.AnalysisSegmentData;
import org.example.voice.analysis.domain.model.AnalysisSegmentPageData;
import org.example.voice.analysis.domain.port.AnalysisSegmentReader;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AnalysisSegmentReaderImpl implements AnalysisSegmentReader {

    private final AnalysisSegmentJpaRepository repository;

    public List<AnalysisSegment> findPage(Long analysisId, int page, int size) {
        return repository.findByAnalysisResultIdOrderBySequenceNoAscIdAsc(analysisId, PageRequest.of(page, size));
    }

    public long count(Long analysisId) {
        return repository.countByAnalysisResultId(analysisId);
    }

    @Override
    @Cacheable(
            cacheNames = AnalysisCacheNames.SEGMENTS,
            key = "T(org.example.voice.analysis.infrastructure.cache.AnalysisCacheKeys).segments(#p1, #p0, #p2, #p3)"
    )
    public AnalysisSegmentPageData findPageData(Long analysisId, Long userId, int page, int size) {
        List<AnalysisSegmentData> items = repository
                .findByAnalysisResultIdOrderBySequenceNoAscIdAsc(analysisId, PageRequest.of(page, size))
                .stream()
                .map(this::toData)
                .toList();
        return new AnalysisSegmentPageData(items, page, size, repository.countByAnalysisResultId(analysisId));
    }

    private AnalysisSegmentData toData(AnalysisSegment segment) {
        return new AnalysisSegmentData(
                segment.getId(),
                segment.getSequenceNo(),
                segment.getExpectedText(),
                segment.getRecognizedText(),
                segment.getStartMs(),
                segment.getEndMs(),
                segment.getMatchType(),
                segment.getResultStatus(),
                segment.getTargetUnit(),
                segment.getErrorType(),
                segment.getPronunciationScore(),
                segment.getIntonationScore(),
                segment.getFeedback()
        );
    }
}
