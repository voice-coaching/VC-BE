ALTER TABLE analysis_results
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE analysis_results
    ADD CONSTRAINT ck_analysis_results_retry_count
        CHECK (retry_count BETWEEN 0 AND 3);
