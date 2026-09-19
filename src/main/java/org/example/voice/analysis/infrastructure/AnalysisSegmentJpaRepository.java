package org.example.voice.analysis.infrastructure;

import org.example.voice.analysis.domain.entity.AnalysisSegment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnalysisSegmentJpaRepository extends JpaRepository<AnalysisSegment, Long> {
    List<AnalysisSegment> findByAnalysisResultIdOrderBySequenceNoAscIdAsc(Long analysisId, Pageable pageable);
    long countByAnalysisResultId(Long analysisId);
    void deleteByAnalysisResultId(Long analysisId);
}
