-- Sandbox ledger only. Never use this table as a production subscription entitlement source.
CREATE TABLE payment_test_orders (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(64) NOT NULL,
    product VARCHAR(40) NOT NULL CHECK (product = 'SPEAKAI_MONTHLY_TEST_V1'),
    amount BIGINT NOT NULL CHECK (amount = 15000),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'KRW'),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    provider_updated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_test_payment_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_test_payment_period CHECK ((paid_at IS NULL AND expires_at IS NULL) OR (paid_at IS NOT NULL AND expires_at > paid_at)),
    CONSTRAINT ck_test_paid_period CHECK (status <> 'PAID' OR paid_at IS NOT NULL)
);
CREATE INDEX ix_test_payment_membership ON payment_test_orders(user_id, status, expires_at);
CREATE TABLE payment_test_webhook_receipts (
    id VARCHAR(200) PRIMARY KEY,
    payload_hash VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
