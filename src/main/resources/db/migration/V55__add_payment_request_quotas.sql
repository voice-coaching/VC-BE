-- Bounded state: at most four reusable rows per member, no payment history changes.
CREATE TABLE payment_request_quotas (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation VARCHAR(16) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL,
    CONSTRAINT uq_payment_request_quota UNIQUE (user_id, operation),
    CONSTRAINT ck_payment_request_quota_operation CHECK (operation IN ('CREATE', 'VERIFY', 'READ', 'CANCEL')),
    CONSTRAINT ck_payment_request_quota_attempts CHECK (
        attempts >= 0 AND attempts <= CASE operation
            WHEN 'READ' THEN 60 WHEN 'VERIFY' THEN 10 ELSE 5 END
    ),
    CONSTRAINT ck_payment_request_quota_id CHECK (id = user_id::text || ':' || operation)
);
