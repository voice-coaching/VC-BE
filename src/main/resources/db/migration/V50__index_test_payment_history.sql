CREATE INDEX ix_test_payment_history
    ON payment_test_orders (user_id, created_at DESC, id DESC);
