package org.example.voice.analysis.controller.dto;

import org.example.voice.analysis.domain.model.AnalysisCoaching;
import java.util.List;

/** Public projection of the stored coaching document, never raw provider output. */
public record AnalysisCoachingResponseDto(String schemaVersion, String status, String summary,
        List<String> strengths, List<AnalysisCoaching.Item> items, String practicePlan,
        List<String> limitations, String comparison, AnalysisCoaching.Coverage coverage,
        AnalysisCoaching.Score score, AnalysisCoaching.Visual visual, AnalysisCoaching.Generation generation) {
    public static AnalysisCoachingResponseDto from(AnalysisCoaching source) {
        return source == null ? null : new AnalysisCoachingResponseDto(source.schemaVersion(), source.status(),
                source.summary(), source.strengths(), source.items(), source.practicePlan(), source.limitations(),
                source.comparison(), source.coverage(), source.score(), source.visual(), source.generation());
    }
}
