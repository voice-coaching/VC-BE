ALTER TABLE analysis_results
    ADD COLUMN IF NOT EXISTS feedback_regeneration_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS feedback_regenerated_at TIMESTAMP WITH TIME ZONE;
