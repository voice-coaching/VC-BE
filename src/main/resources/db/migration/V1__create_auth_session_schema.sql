CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_refresh_tokens_user ON refresh_tokens (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE UNIQUE INDEX IF NOT EXISTS uk_refresh_tokens_session ON refresh_tokens (session_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_social_accounts_provider_user
    ON social_accounts (provider, provider_user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_normalized
    ON users (LOWER(email)) WHERE email IS NOT NULL;
