package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.example.voice.practicecontent.domain.type.LearningFocus;
import org.example.voice.training.domain.type.RecordingQualityStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Shared initial rollout source scope; not admission readiness or authorization. */
@Component
@RequiredArgsConstructor
public final class CanonicalRequestScope {
    private final JdbcTemplate jdbc;
    public boolean eligible(AnalysisResult result) {
        var recording=result.getRecording();var session=recording.getTrainingSession();
        return recording.getVisualObjectKey()==null && recording.getQualityStatus()==RecordingQualityStatus.PASS
                && session.getCourseStepId()==null && session.getCourseEducationRevisionId()==null
                && session.getLearningFocus()==LearningFocus.PRONUNCIATION
                && !Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM title_exams WHERE training_session_id=?)",Boolean.class,session.getId()));
    }
}
