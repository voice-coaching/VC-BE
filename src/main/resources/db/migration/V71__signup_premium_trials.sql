-- Created only by an authenticated, explicit activation. Never auto-grant at signup.
CREATE TABLE user_premium_trials (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT premium_trial_exact_duration CHECK (expires_at = starts_at + INTERVAL '72 hours')
);
