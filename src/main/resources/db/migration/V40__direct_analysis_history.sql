-- Independent public RunPod jobs: no synthetic training/recording/analysis IDs.
CREATE TABLE direct_analysis_history (
    job_id UUID PRIMARY KEY,
    execution_id UUID NOT NULL,
    claim_sha256 VARCHAR(64) NOT NULL,
    result_sha256 VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    script_text TEXT NOT NULL,
    content_id BIGINT,
    archive_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMPTZ
);
CREATE TABLE direct_analysis_history_links (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL,
    claim_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, job_id)
);
CREATE INDEX direct_analysis_history_links_recent ON direct_analysis_history_links(user_id, created_at DESC);
