-- Additive reader-first migration. Existing results and event digests stay intact.
ALTER TABLE analysis_results ADD COLUMN coaching_document JSONB;
ALTER TABLE analysis_results ADD CONSTRAINT ck_analysis_coaching_document
    CHECK (coaching_document IS NULL OR
           COALESCE(jsonb_typeof(coaching_document) = 'object' AND
            coaching_document ->> 'schemaVersion' = 'voice-coaching.coaching-result.v1', FALSE));
