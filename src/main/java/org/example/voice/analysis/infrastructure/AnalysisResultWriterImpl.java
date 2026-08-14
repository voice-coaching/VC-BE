package org.example.voice.analysis.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.analysis.domain.port.AnalysisResultWriter;
import org.example.voice.training.infrastructure.AnalysisResultJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnalysisResultWriterImpl implements AnalysisResultWriter {
    private final AnalysisResultJpaRepository repository;
    public AnalysisResult save(AnalysisResult result) { return repository.save(result); }
}
