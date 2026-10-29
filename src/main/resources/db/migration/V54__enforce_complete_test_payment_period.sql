-- CHECK accepts UNKNOWN, so both branches must explicitly handle nullable dates.
-- Validate existing rows without rewriting payment or cancellation history.
ALTER TABLE payment_test_orders
    DROP CONSTRAINT ck_test_payment_period,
    DROP CONSTRAINT ck_test_paid_period,
    ADD CONSTRAINT ck_test_payment_period CHECK (
        (paid_at IS NULL AND expires_at IS NULL)
        OR (paid_at IS NOT NULL AND expires_at IS NOT NULL AND expires_at > paid_at)
    ),
    ADD CONSTRAINT ck_test_paid_period CHECK (
        status <> 'PAID' OR (paid_at IS NOT NULL AND expires_at IS NOT NULL)
    );
