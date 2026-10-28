-- One durable, full-cancellation command per TEST order. No production entitlement ledger.
CREATE TABLE payment_test_cancellations (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE REFERENCES payment_test_orders(id),
    reason VARCHAR(200) NOT NULL CHECK (length(trim(reason)) BETWEEN 1 AND 200),
    status VARCHAR(16) NOT NULL CHECK (status IN ('UNKNOWN', 'PENDING', 'SUCCEEDED', 'FAILED')),
    requested_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
