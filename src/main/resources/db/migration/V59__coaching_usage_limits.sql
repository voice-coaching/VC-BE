-- A fixed rollout timestamp preserves the one-time trial across restarts and deployments.
CREATE TABLE coaching_usage_policy (
    id SMALLINT PRIMARY KEY CHECK (id=1),
    effective_at TIMESTAMPTZ NOT NULL
);
INSERT INTO coaching_usage_policy VALUES (1, CURRENT_TIMESTAMP);

-- Keep the receipt when a recording/analysis/history is deleted: deleting a result is not a refund.
-- Logical analysis/recording identities intentionally have no FK; the adapter validates ownership on insert.
CREATE TABLE coaching_usage_reservations (
    analysis_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recording_id BIGINT NOT NULL UNIQUE,
    request_event_id UUID NOT NULL UNIQUE,
    window_key VARCHAR(80) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    window_expires_at TIMESTAMPTZ NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('RESERVED','CONSUMED','RELEASED')),
    CHECK (window_expires_at > window_started_at),
    CHECK (window_key='free' OR window_key LIKE 'paid:%')
);
CREATE INDEX ix_coaching_usage_window ON coaching_usage_reservations(user_id,window_key,state);
ALTER TABLE analysis_results ADD COLUMN coaching_delivered BOOLEAN NOT NULL DEFAULT FALSE;

-- All analysis writers (JPA, legacy streams, canonical callbacks and SQL timeout/failure lanes)
-- converge here in the same transaction. This projects terminal outcome; admission and plan rules
-- remain in the application. No old analysis is inserted/backfilled or retroactively charged.
CREATE FUNCTION settle_coaching_usage() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE delivered BOOLEAN := FALSE;
BEGIN
    IF TG_OP='DELETE' THEN
        UPDATE coaching_usage_reservations SET state='RELEASED'
        WHERE analysis_id=OLD.id AND state='RESERVED';
        RETURN OLD;
    END IF;
    IF NEW.status NOT IN ('COMPLETED','FAILED') THEN RETURN NEW; END IF;
    IF NEW.status='COMPLETED' THEN
        IF NEW.canonical_result_event_id IS NULL THEN
            delivered := NEW.analysis_outcome IN ('COACHING_READY','COMPLETED_NO_ISSUE');
        ELSE
            delivered := NEW.coaching_delivered;
        END IF;
    END IF;
    UPDATE coaching_usage_reservations
    SET state=CASE WHEN delivered IS TRUE THEN 'CONSUMED' ELSE 'RELEASED' END
    WHERE analysis_id=NEW.id AND request_event_id::text=NEW.active_request_event_id AND state='RESERVED';
    RETURN NEW;
END;
$$;
CREATE TRIGGER coaching_usage_terminal
    AFTER UPDATE OF status,analysis_outcome,canonical_result_event_id,coaching_delivered ON analysis_results
    FOR EACH ROW EXECUTE FUNCTION settle_coaching_usage();
CREATE TRIGGER coaching_usage_analysis_deleted
    BEFORE DELETE ON analysis_results FOR EACH ROW EXECUTE FUNCTION settle_coaching_usage();
