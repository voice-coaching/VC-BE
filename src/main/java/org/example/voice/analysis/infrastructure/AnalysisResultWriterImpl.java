package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.port.AnalysisResultWriter;
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AnalysisResultWriterImpl implements AnalysisResultWriter {

    private final AnalysisResultJpaRepository repository;

    @Override
    public Optional<AnalysisResult> findByIdForUpdate(Long analysisId) {
        return repository.findByIdForUpdate(analysisId);
    }

    @Override
    @CacheEvict(cacheNames = {
            AnalysisCacheNames.DETAIL,
            AnalysisCacheNames.SESSION_RESULT,
            AnalysisCacheNames.SEGMENTS
    }, allEntries = true)
    public AnalysisResult save(AnalysisResult result) { return repository.save(result); }
}
