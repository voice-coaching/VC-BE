-- Explicit administrator DB-only erasure. No external file deletion or broad FK cascade.
-- V61 already removed the historic application retention guards. Do not reinstate them here.
CREATE TABLE admin_member_deletion_receipts (
    user_id BIGINT PRIMARY KEY,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Shared administrator publications survive; only the deleted author reference is cleared.
ALTER TABLE admin_notification_campaigns ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE admin_popups ALTER COLUMN created_by DROP NOT NULL;
-- Audit history stays immutable except deletion of records belonging to the member being erased.
-- A new minimal USER_DELETE audit is appended by the authenticated service in the same transaction.
CREATE OR REPLACE FUNCTION prevent_admin_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target BIGINT;
BEGIN
    IF TG_OP='DELETE' THEN
        target:=NULLIF(current_setting('voice.admin_purge_user',true),'')::BIGINT;
        IF target IS NOT NULL AND EXISTS(SELECT 1 FROM users WHERE id=target)
           AND (OLD.actor_user_id=target OR (OLD.target_type='USER' AND OLD.target_id=target::TEXT)) THEN
            RETURN OLD;
        END IF;
    END IF;
    RAISE EXCEPTION 'admin audit records are immutable' USING ERRCODE='23514';
END; $$;

CREATE FUNCTION admin_purge_member_database(target BIGINT) RETURNS TIMESTAMPTZ LANGUAGE plpgsql AS $$
DECLARE finished TIMESTAMPTZ := CURRENT_TIMESTAMP; private_caches TEXT[];
BEGIN
    -- Caller already validates current administrator, target confirmation and timestamp.
    -- Repeat the row lock here; deleted users have a minimal receipt for idempotent retries.
    PERFORM id FROM users WHERE id=target FOR UPDATE;
    IF NOT FOUND THEN
        SELECT deleted_at INTO finished FROM admin_member_deletion_receipts WHERE user_id=target;
        IF finished IS NULL THEN RAISE EXCEPTION 'member not found' USING ERRCODE='P0002'; END IF;
        RETURN finished;
    END IF;
    -- V61 allows legacy inconsistent references. Never erase another member's or the shared catalog's content.
    IF EXISTS(SELECT 1 FROM practice_contents c WHERE c.owner_id=target AND (
        EXISTS(SELECT 1 FROM training_sessions s WHERE s.content_id=c.id AND s.user_id<>target)
        OR EXISTS(SELECT 1 FROM script_library_entries l WHERE l.content_id=c.id AND l.user_id<>target)
        OR EXISTS(SELECT 1 FROM script_library_requests q WHERE q.content_id=c.id AND q.user_id<>target)
        OR EXISTS(SELECT 1 FROM title_exams t WHERE t.practice_content_id=c.id AND t.user_id<>target)
        OR EXISTS(SELECT 1 FROM course_steps s WHERE s.practice_content_id=c.id)
        OR EXISTS(SELECT 1 FROM practice_examples e WHERE e.content_id=c.id)
        OR EXISTS(SELECT 1 FROM title_policies p WHERE p.practice_content_id=c.id))) THEN
        RAISE EXCEPTION 'private content is shared or has inconsistent ownership' USING ERRCODE='23503';
    END IF;
    PERFORM s.id FROM training_sessions s WHERE s.user_id=target ORDER BY s.id FOR UPDATE;
    PERFORM r.id FROM voice_recordings r JOIN training_sessions s ON s.id=r.training_session_id WHERE s.user_id=target ORDER BY r.id FOR UPDATE OF r;
    PERFORM a.id FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id JOIN training_sessions s ON s.id=r.training_session_id WHERE s.user_id=target ORDER BY a.id FOR UPDATE OF a;
    CREATE TEMP TABLE admin_member_purge_executions(execution_id UUID NOT NULL,user_id BIGINT NOT NULL,request_id TEXT NOT NULL,PRIMARY KEY(execution_id,request_id)) ON COMMIT DROP;
    -- Include identifiable legacy snapshots even when V61-era missing FKs left no registry row.
    WITH owned_analyses AS (
        SELECT a.id FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id JOIN training_sessions s ON s.id=r.training_session_id WHERE s.user_id=target
        UNION SELECT analysis_id FROM analysis_canonical_result_effects WHERE user_id=target
    ), owned_recordings AS (
        SELECT r.id FROM voice_recordings r JOIN training_sessions s ON s.id=r.training_session_id WHERE s.user_id=target
    ), snapshots AS (
        SELECT execution_id,request_id::TEXT,analysis_id,recording_id FROM analysis_canonical_executions
        UNION SELECT execution_id,request_id::TEXT,analysis_id,recording_id FROM analysis_canonical_results
        UNION SELECT execution_id,request_id::TEXT,analysis_id,NULL::BIGINT FROM analysis_canonical_callback_inbox
        UNION SELECT execution_id,request_id::TEXT,analysis_id,NULL::BIGINT FROM analysis_evidence_receipts
        UNION SELECT execution_id,request_id::TEXT,analysis_id,NULL::BIGINT FROM analysis_canonical_journals
        UNION SELECT execution_id,request_id::TEXT,analysis_id,NULL::BIGINT FROM analysis_canonical_handoffs
        UNION SELECT execution_id,NULL::TEXT,NULL::BIGINT,recording_id FROM analysis_execution_media
    )
    INSERT INTO pg_temp.admin_member_purge_executions
    SELECT DISTINCT x.execution_id,target,COALESCE(x.request_id,'') FROM snapshots x
    WHERE x.execution_id IS NOT NULL AND (x.analysis_id IN(SELECT id FROM owned_analyses) OR x.recording_id IN(SELECT id FROM owned_recordings))
      AND NOT EXISTS(SELECT 1 FROM voice_recordings r JOIN training_sessions s ON s.id=r.training_session_id WHERE r.id=x.recording_id AND s.user_id<>target)
      AND NOT EXISTS(SELECT 1 FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id JOIN training_sessions s ON s.id=r.training_session_id WHERE a.id=x.analysis_id AND s.user_id<>target)
      AND NOT EXISTS(SELECT 1 FROM analysis_canonical_executions e JOIN voice_recordings r ON r.id=e.recording_id JOIN training_sessions s ON s.id=r.training_session_id WHERE e.execution_id=x.execution_id AND s.user_id<>target)
      AND NOT EXISTS(SELECT 1 FROM snapshots y JOIN voice_recordings r ON r.id=y.recording_id JOIN training_sessions s ON s.id=r.training_session_id WHERE y.execution_id=x.execution_id AND s.user_id<>target)
      AND NOT EXISTS(SELECT 1 FROM snapshots y JOIN analysis_results a ON a.id=y.analysis_id JOIN voice_recordings r ON r.id=a.recording_id JOIN training_sessions s ON s.id=r.training_session_id WHERE y.execution_id=x.execution_id AND s.user_id<>target)
      AND NOT EXISTS(SELECT 1 FROM analysis_canonical_results c JOIN analysis_canonical_result_effects f ON f.event_id=c.event_id WHERE c.execution_id=x.execution_id AND f.user_id<>target);
    DELETE FROM analysis_cancellation_outbox WHERE request_event_id IN(SELECT request_id FROM pg_temp.admin_member_purge_executions WHERE request_id<>'');

    DELETE FROM analysis_canonical_callback_apply WHERE event_id IN(SELECT event_id FROM analysis_canonical_callback_inbox WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions));
    DELETE FROM analysis_canonical_ack_journal WHERE event_id IN(SELECT event_id FROM analysis_canonical_results WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions));
    DELETE FROM analysis_canonical_result_effects WHERE user_id=target OR event_id IN(SELECT event_id FROM analysis_canonical_results WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions));
    DELETE FROM analysis_canonical_results WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions);
    DELETE FROM analysis_canonical_callback_inbox WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions);
    DELETE FROM analysis_evidence_receipts WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions);
    DELETE FROM analysis_canonical_archive_artifacts WHERE handoff_id IN(SELECT handoff_id FROM analysis_canonical_handoffs WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions));
    DELETE FROM analysis_canonical_archive_jobs WHERE handoff_id IN(SELECT handoff_id FROM analysis_canonical_handoffs WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions));
    DELETE FROM analysis_canonical_handoff_artifacts WHERE handoff_id IN(SELECT handoff_id FROM analysis_canonical_handoffs WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions));
    DELETE FROM analysis_canonical_handoffs WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions);
    DELETE FROM analysis_canonical_upload_journal WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions);
    DELETE FROM analysis_canonical_journals WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions);
    DELETE FROM analysis_execution_media WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions);
    DELETE FROM analysis_canonical_executions WHERE execution_id IN(SELECT execution_id FROM pg_temp.admin_member_purge_executions);
    DROP TABLE pg_temp.admin_member_purge_executions;

    DELETE FROM analysis_cancellation_outbox WHERE request_event_id IN(SELECT o.event_id FROM analysis_request_outbox o JOIN analysis_results a ON a.id=o.analysis_id JOIN voice_recordings r ON r.id=a.recording_id JOIN training_sessions s ON s.id=r.training_session_id WHERE s.user_id=target) OR request_event_id IN(SELECT a.active_request_event_id FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id JOIN training_sessions s ON s.id=r.training_session_id WHERE s.user_id=target);
    DELETE FROM analysis_request_outbox WHERE analysis_id IN(SELECT a.id FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id JOIN training_sessions s ON s.id=r.training_session_id WHERE s.user_id=target);
    DELETE FROM analysis_segments WHERE analysis_result_id IN(SELECT a.id FROM analysis_results a JOIN voice_recordings r ON r.id=a.recording_id JOIN training_sessions s ON s.id=r.training_session_id WHERE s.user_id=target);
    DELETE FROM analysis_results WHERE recording_id IN(SELECT r.id FROM voice_recordings r JOIN training_sessions s ON s.id=r.training_session_id WHERE s.user_id=target);
    DELETE FROM recording_media_preparations WHERE recording_id IN(SELECT r.id FROM voice_recordings r JOIN training_sessions s ON s.id=r.training_session_id WHERE s.user_id=target);
    DELETE FROM voice_recordings WHERE training_session_id IN(SELECT id FROM training_sessions WHERE user_id=target);
    DELETE FROM title_exam_requests WHERE user_id=target;
    DELETE FROM title_exams WHERE user_id=target;
    DELETE FROM training_sessions WHERE user_id=target;

    -- Public jobs may have links owned by another member. Never remove that member's result.
    DELETE FROM direct_analysis_history h WHERE EXISTS(SELECT 1 FROM direct_analysis_history_links l WHERE l.job_id=h.job_id AND l.user_id=target)
      AND NOT EXISTS(SELECT 1 FROM direct_analysis_history_links l WHERE l.job_id=h.job_id AND l.user_id<>target);
    DELETE FROM direct_analysis_history_links WHERE user_id=target;
    DELETE FROM payment_test_cancellations WHERE order_id IN(SELECT id FROM payment_test_orders WHERE user_id=target);
    DELETE FROM payment_test_orders WHERE user_id=target;
    DELETE FROM script_library_requests WHERE user_id=target;
    DELETE FROM script_library_entries WHERE user_id=target;
    DELETE FROM script_library_topics WHERE user_id=target;
    DELETE FROM custom_content_requests WHERE user_id=target;
    SELECT array_agg(cache_key) INTO private_caches FROM example_tts_jobs WHERE content_id IN(SELECT id FROM practice_contents WHERE owner_id=target) AND cache_key IS NOT NULL;
    DELETE FROM example_audio_approvals WHERE job_id IN(SELECT id FROM example_tts_jobs WHERE content_id IN(SELECT id FROM practice_contents WHERE owner_id=target));
    DELETE FROM example_tts_jobs WHERE content_id IN(SELECT id FROM practice_contents WHERE owner_id=target);
    DELETE FROM example_audio_cache c WHERE c.id=ANY(private_caches) AND NOT EXISTS(SELECT 1 FROM example_tts_jobs j WHERE j.cache_key=c.id);
    DELETE FROM content_tts_outbox WHERE content_id IN(SELECT id FROM practice_contents WHERE owner_id=target);
    DELETE FROM admin_reference_audio_uploads WHERE actor_user_id=target OR content_id IN(SELECT id FROM practice_contents WHERE owner_id=target);
    DELETE FROM reference_audios WHERE content_id IN(SELECT id FROM practice_contents WHERE owner_id=target);
    DELETE FROM practice_contents WHERE owner_id=target;
    DELETE FROM processing_consents WHERE user_id=target;
    DELETE FROM recording_deletion_outbox WHERE user_id=target;
    DELETE FROM recording_upload_intents WHERE user_id=target;
    DELETE FROM profile_images WHERE user_id=target;
    DELETE FROM inquiries WHERE user_id=target;
    DELETE FROM user_course_progress WHERE user_id=target;
    DELETE FROM user_titles WHERE user_id=target;
    DELETE FROM notification_preferences WHERE user_id=target;
    DELETE FROM user_notifications WHERE user_id=target;
    DELETE FROM push_registration_requests WHERE user_id=target;
    DELETE FROM push_subscriptions WHERE user_id=target;
    DELETE FROM coaching_usage_reservations WHERE user_id=target;
    DELETE FROM payment_request_quotas WHERE user_id=target;
    DELETE FROM user_test_membership_grants WHERE user_id=target;
    DELETE FROM onboarding_profiles WHERE user_id=target;
    DELETE FROM social_accounts WHERE user_id=target;
    DELETE FROM refresh_tokens WHERE user_id=target;
    UPDATE admin_notification_campaigns SET version=version+1 WHERE id IN(SELECT campaign_id FROM admin_notification_recipients WHERE user_id=target);
    DELETE FROM admin_notification_recipients WHERE user_id=target;
    PERFORM set_config('voice.admin_purge_user',target::TEXT,true);
    DELETE FROM admin_audit_logs WHERE actor_user_id=target OR (target_type='USER' AND target_id=target::TEXT);
    PERFORM set_config('voice.admin_purge_user','',true);
    UPDATE admin_notification_campaigns SET created_by=NULL,version=version+1 WHERE created_by=target;
    UPDATE admin_popups SET created_by=NULL,version=version+1 WHERE created_by=target;
    INSERT INTO admin_member_deletion_receipts(user_id,deleted_at) VALUES(target,finished);
    DELETE FROM users WHERE id=target;
    RETURN finished;
END; $$;
