-- Explicit policy change: remove application validation, ownership-FK, uniqueness,
-- immutability/retention triggers and required-field constraints. Existing rows are retained.
-- Resource primary keys and SQL data types remain the addressing/storage format.
DO $remove_guards$
DECLARE
    item RECORD;
    application_tables TEXT[] := ARRAY[
        'analysis_cancellation_outbox',
        'analysis_canonical_ack_journal',
        'analysis_canonical_archive_artifacts',
        'analysis_canonical_archive_jobs',
        'analysis_canonical_callback_apply',
        'analysis_canonical_callback_inbox',
        'analysis_canonical_executions',
        'analysis_canonical_handoff_artifacts',
        'analysis_canonical_handoffs',
        'analysis_canonical_journals',
        'analysis_canonical_result_effects',
        'analysis_canonical_results',
        'analysis_canonical_upload_journal',
        'analysis_dispatch_gates',
        'analysis_evidence_receipts',
        'analysis_execution_media',
        'analysis_request_outbox',
        'analysis_results',
        'analysis_segments',
        'coaching_usage_policy',
        'coaching_usage_reservations',
        'content_categories',
        'content_tts_outbox',
        'course_step_revisions',
        'course_steps',
        'courses',
        'custom_content_requests',
        'direct_analysis_history',
        'direct_analysis_history_links',
        'example_audio_approvals',
        'example_audio_cache',
        'example_tts_jobs',
        'example_tts_outbox',
        'example_tts_profiles',
        'example_tts_quotas',
        'inquiries',
        'news_popularity_items',
        'news_popularity_snapshots',
        'notices',
        'notification_preferences',
        'onboarding_profiles',
        'payment_request_quotas',
        'payment_test_cancellations',
        'payment_test_orders',
        'payment_test_webhook_receipts',
        'practice_contents',
        'practice_example_sets',
        'practice_examples',
        'processing_consents',
        'profile_images',
        'push_registration_requests',
        'push_subscriptions',
        'recording_deletion_outbox',
        'recording_media_preparations',
        'recording_upload_intents',
        'reference_audios',
        'refresh_tokens',
        'script_library_entries',
        'script_library_requests',
        'script_library_topics',
        'social_accounts',
        'title_exam_requests',
        'title_exams',
        'title_policies',
        'training_sessions',
        'user_course_progress',
        'user_notifications',
        'user_test_membership_grants',
        'user_titles',
        'users',
        'voice_recordings'
    ];
BEGIN
    -- Foreign keys first, because unique indexes can be referenced by them.
    FOR item IN
        SELECT c.conrelid::regclass AS relation, c.conname
        FROM pg_constraint c JOIN pg_class r ON r.oid=c.conrelid
        JOIN pg_namespace n ON n.oid=r.relnamespace
        WHERE n.nspname=current_schema() AND r.relname=ANY(application_tables) AND c.contype='f'
    LOOP EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',item.relation,item.conname); END LOOP;
    FOR item IN
        SELECT c.conrelid::regclass AS relation, c.conname
        FROM pg_constraint c JOIN pg_class r ON r.oid=c.conrelid
        JOIN pg_namespace n ON n.oid=r.relnamespace
        WHERE n.nspname=current_schema() AND r.relname=ANY(application_tables) AND c.contype IN ('c','u','x')
    LOOP EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',item.relation,item.conname); END LOOP;
    FOR item IN
        SELECT t.tgrelid::regclass AS relation,t.tgname
        FROM pg_trigger t JOIN pg_class r ON r.oid=t.tgrelid JOIN pg_namespace n ON n.oid=r.relnamespace
        WHERE n.nspname=current_schema() AND r.relname=ANY(application_tables) AND NOT t.tgisinternal
    LOOP EXECUTE format('DROP TRIGGER %I ON %s',item.tgname,item.relation); END LOOP;
    FOR item IN
        SELECT i.indexrelid::regclass AS index_name
        FROM pg_index i JOIN pg_class r ON r.oid=i.indrelid JOIN pg_namespace n ON n.oid=r.relnamespace
        WHERE n.nspname=current_schema() AND r.relname=ANY(application_tables) AND i.indisunique AND NOT i.indisprimary
    LOOP EXECUTE format('DROP INDEX %s',item.index_name); END LOOP;
    FOR item IN
        SELECT a.attrelid::regclass AS relation,a.attname
        FROM pg_attribute a JOIN pg_class r ON r.oid=a.attrelid JOIN pg_namespace n ON n.oid=r.relnamespace
        WHERE n.nspname=current_schema() AND r.relname=ANY(application_tables) AND a.attnum>0 AND a.attnotnull
          AND NOT a.attisdropped AND NOT EXISTS (
              SELECT 1 FROM pg_index i WHERE i.indrelid=a.attrelid AND i.indisprimary AND a.attnum=ANY(i.indkey))
    LOOP EXECUTE format('ALTER TABLE %s ALTER COLUMN %I DROP NOT NULL',item.relation,item.attname); END LOOP;
END;
$remove_guards$;
