CREATE TABLE news_popularity_snapshots (
    ranking_date DATE PRIMARY KEY,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    next_update_at TIMESTAMPTZ NOT NULL,
    ranking_basis VARCHAR(30) NOT NULL CHECK (ranking_basis IN ('CATALOG_ORDER', 'COMPLETED_USERS')),
    CHECK (period_start < period_end AND period_end < next_update_at)
);

CREATE TABLE news_popularity_items (
    ranking_date DATE NOT NULL REFERENCES news_popularity_snapshots(ranking_date) ON DELETE CASCADE,
    content_id BIGINT NOT NULL REFERENCES practice_contents(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position > 0),
    usage_count BIGINT NOT NULL CHECK (usage_count >= 0),
    PRIMARY KEY (ranking_date, content_id),
    UNIQUE (ranking_date, position)
);

CREATE INDEX idx_news_popularity_completed_window
    ON training_sessions (completed_at, content_id, user_id)
    WHERE status = 'COMPLETED';
