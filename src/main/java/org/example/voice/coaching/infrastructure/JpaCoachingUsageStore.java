package org.example.voice.coaching.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.example.voice.coaching.domain.CoachingWindow;
import org.example.voice.coaching.domain.port.CoachingUsageStore;
import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.example.voice.user.domain.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaCoachingUsageStore implements CoachingUsageStore {
    private final EntityManager em;
    private final JdbcTemplate jdbc;

    @Override public void lockAccount(Long userId) {

        // Flush the pending/retry generation before any JDBC usage operation.
        em.flush();
    }

    @Override public OffsetDateTime trialStartedAt(Long userId) {
        var user = em.find(User.class, userId);

        var rollout = jdbc.queryForObject("SELECT effective_at FROM coaching_usage_policy WHERE id=1",
                OffsetDateTime.class);
        return user.getCreatedAt().isAfter(rollout) ? user.getCreatedAt() : rollout;
    }

    @Override public Counts counts(Long userId, String windowKey) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE state='CONSUMED'), count(*) FILTER (WHERE state='RESERVED')
                FROM coaching_usage_reservations WHERE user_id=? AND window_key=?
                """, (rs, row) -> new Counts(rs.getLong(1), rs.getLong(2)), userId, windowKey);
    }

    @Override public void reserve(Long userId, Long analysisId, Long recordingId, UUID requestId, CoachingWindow window) {
        int changed = jdbc.update("""
                INSERT INTO coaching_usage_reservations
                    (analysis_id,user_id,recording_id,request_event_id,window_key,window_started_at,window_expires_at,state)
                SELECT a.id,t.user_id,r.id,CAST(? AS uuid),?,?,?,'RESERVED'
                FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id
                JOIN training_sessions t ON t.id=r.training_session_id
                WHERE a.id=? AND t.user_id=? AND r.id=? AND a.active_request_event_id=?
                    AND a.status='PENDING' ON CONFLICT (analysis_id) DO UPDATE SET user_id=EXCLUDED.user_id,recording_id=EXCLUDED.recording_id,request_event_id=EXCLUDED.request_event_id,window_key=EXCLUDED.window_key,window_started_at=EXCLUDED.window_started_at,window_expires_at=EXCLUDED.window_expires_at,state=EXCLUDED.state
""", requestId, window.key(), window.startsAt(), window.expiresAt(),
                analysisId, userId, recordingId, requestId.toString());

    }
}
