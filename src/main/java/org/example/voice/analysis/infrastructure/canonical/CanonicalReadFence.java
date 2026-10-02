package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Canonical and subsequent retry generations must never trust an old legacy cache entry. */
@Component("canonicalReadFence")
@RequiredArgsConstructor
public class CanonicalReadFence {
    private final JdbcTemplate jdbc;
    public boolean hasHistory(Long analysisId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM analysis_canonical_executions WHERE analysis_id=?)",Boolean.class,analysisId));
    }
    public boolean sessionHasHistory(Long sessionId,Long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM analysis_canonical_executions e
                    JOIN analysis_results a ON a.id=e.analysis_id
                    JOIN voice_recordings r ON r.id=a.recording_id
                    JOIN training_sessions s ON s.id=r.training_session_id
                    WHERE s.id=? AND s.user_id=?)
                """,Boolean.class,sessionId,userId));
    }
    public boolean visible(Long analysisId,Long userId) {
        if(!hasHistory(analysisId))return true;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM analysis_results a
                    JOIN voice_recordings r ON r.id=a.recording_id
                    JOIN training_sessions s ON s.id=r.training_session_id
                    JOIN users u ON u.id=s.user_id JOIN practice_contents c ON c.id=s.content_id
                    WHERE a.id=? AND u.id=? AND r.deleted_at IS NULL AND r.is_selected=TRUE
                        AND s.status<>'CANCELED' AND u.status='ACTIVE' AND u.deleted_at IS NULL
                        AND c.custom_deleted_at IS NULL
                        AND (a.failure_code IS NULL OR a.failure_code NOT LIKE '%cancel%'))
                """,Boolean.class,analysisId,userId));
    }
}
