package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisSegment;
import org.example.voice.analysis.domain.port.AnalysisSegmentReader;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AnalysisSegmentReaderImpl implements AnalysisSegmentReader {
    private final AnalysisSegmentJpaRepository repository;
    public List<AnalysisSegment> findPage(Long analysisId, int page, int size) { return repository.findByAnalysisResultIdOrderBySequenceNoAscIdAsc(analysisId, PageRequest.of(page, size)); }
    public long count(Long analysisId) { return repository.countByAnalysisResultId(analysisId); }
}
