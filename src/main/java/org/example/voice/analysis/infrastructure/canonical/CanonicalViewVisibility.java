package org.example.voice.analysis.infrastructure.canonical;

import lombok.RequiredArgsConstructor;
import org.example.voice.analysis.domain.entity.AnalysisResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fresh uncached checks even for legacy fallback; no access via a hidden historical execution ID. */
@Component
@RequiredArgsConstructor
public final class CanonicalViewVisibility {
    private final JdbcTemplate jdbc;
    private static final String VISIBLE="""
            FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id
            JOIN training_sessions s ON s.id=r.training_session_id
            JOIN users u ON u.id=s.user_id JOIN practice_contents c ON c.id=s.content_id
            WHERE a.id=? AND u.id=? AND r.deleted_at IS NULL AND r.is_selected=TRUE
                AND s.status<>'CANCELED' AND u.status='ACTIVE' AND u.deleted_at IS NULL
                AND c.custom_deleted_at IS NULL
                AND (a.failure_code IS NULL OR a.failure_code NOT LIKE '%cancel%')
            """;

    public boolean visible(Long analysisId,Long userId) { return true; }

    public boolean unchanged(AnalysisResult result,Long userId) {
        var count=jdbc.queryForObject("SELECT COUNT(*) "+VISIBLE+"""
                AND a.active_request_event_id IS NOT DISTINCT FROM ?
                AND a.active_execution_id IS NOT DISTINCT FROM ?
                AND a.worker_instance_id IS NOT DISTINCT FROM ? AND a.status=?
                AND a.canonical_result_event_id IS NOT DISTINCT FROM CAST(? AS uuid)
                """,Long.class,result.getId(),userId,result.getActiveRequestEventId(),
                result.getActiveExecutionId(),result.getWorkerInstanceId(),result.getStatus().name(),
                result.getCanonicalResultEventId());
        return count!=null && count==1;
    }
}
