ALTER TABLE analysis_results
    ADD COLUMN IF NOT EXISTS pronunciation_evidence_schema_version VARCHAR(100),
    ADD COLUMN IF NOT EXISTS selected_phone VARCHAR(16),
    ADD COLUMN IF NOT EXISTS selected_expected_index INTEGER,
    ADD COLUMN IF NOT EXISTS selected_start_ms INTEGER,
    ADD COLUMN IF NOT EXISTS selected_end_ms INTEGER,
    ADD COLUMN IF NOT EXISTS detector_score NUMERIC(8,6),
    ADD COLUMN IF NOT EXISTS operating_threshold NUMERIC(8,6),
    ADD COLUMN IF NOT EXISTS score_semantics VARCHAR(100),
    ADD COLUMN IF NOT EXISTS evidence_state VARCHAR(100);

ALTER TABLE analysis_results
    ADD CONSTRAINT ck_analysis_results_selected_phone_index
        CHECK (selected_expected_index IS NULL OR selected_expected_index >= 0),
    ADD CONSTRAINT ck_analysis_results_selected_phone_offsets
        CHECK (
            (selected_start_ms IS NULL AND selected_end_ms IS NULL)
            OR (selected_start_ms >= 0 AND selected_end_ms > selected_start_ms)
        ),
    ADD CONSTRAINT ck_analysis_results_detector_score
        CHECK (detector_score IS NULL OR detector_score BETWEEN 0 AND 1),
    ADD CONSTRAINT ck_analysis_results_operating_threshold
        CHECK (operating_threshold IS NULL OR operating_threshold BETWEEN 0 AND 1),
    ADD CONSTRAINT ck_analysis_results_threshold_passed
        CHECK (
            detector_score IS NULL
            OR operating_threshold IS NULL
            OR detector_score >= operating_threshold
        );
