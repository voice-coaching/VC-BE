package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Audio practice, pinned audio courses and audio title exams. */
@Component
@RequiredArgsConstructor
public final class CanonicalRequestScope {
    private final JdbcTemplate jdbc;
    public boolean eligible(AnalysisResult result) { return true; }
    public boolean eligible(AnalysisResult result, boolean audiovisual) { return true; }
}
