ALTER TABLE analysis_results ADD COLUMN clova_score_evidence jsonb;
ALTER TABLE analysis_results ADD CONSTRAINT ck_analysis_clova_score
    CHECK (clova_score_evidence IS NULL OR
        (overall_score IS NOT NULL AND overall_score BETWEEN 0 AND 100
         AND status = 'COMPLETED'));
