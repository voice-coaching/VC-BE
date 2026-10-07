-- Additive contracts only. Existing immutable bytes and v4/v5 readers remain.
ALTER TABLE analysis_canonical_executions DROP CONSTRAINT canonical_execution_profile_tuple;
ALTER TABLE analysis_canonical_executions ADD CONSTRAINT canonical_execution_profile_tuple CHECK (
 (analysis_profile='CANONICAL_FROZEN_20260928_V4' AND request_schema_version='voice-coaching.runpod-analysis-request.v2' AND result_schema_version='voice-coaching.runpod-analysis-result.v4') OR
 (analysis_profile='CANONICAL_HANDOFF_20261004_V5' AND request_schema_version='voice-coaching.runpod-analysis-request.v3' AND result_schema_version='voice-coaching.runpod-analysis-result.v5') OR
 (analysis_profile='CANONICAL_AUDIOVISUAL_20261007_V6' AND request_schema_version='voice-coaching.runpod-analysis-request.v4' AND result_schema_version='voice-coaching.runpod-analysis-result.v6'));

ALTER TABLE analysis_canonical_results DROP CONSTRAINT canonical_result_transport;
ALTER TABLE analysis_canonical_results ADD CONSTRAINT canonical_result_transport CHECK (
 (schema_version='voice-coaching.runpod-analysis-result.v4' AND analysis_profile='CANONICAL_FROZEN_20260928_V4' AND handoff_id IS NULL AND ((core_sha256 IS NULL)=(evidence_receipt_id IS NULL))) OR
 (schema_version='voice-coaching.runpod-analysis-result.v5' AND analysis_profile='CANONICAL_HANDOFF_20261004_V5' AND handoff_id IS NOT NULL AND evidence_receipt_id IS NULL) OR
 (schema_version='voice-coaching.runpod-analysis-result.v6' AND analysis_profile='CANONICAL_AUDIOVISUAL_20261007_V6' AND handoff_id IS NOT NULL AND evidence_receipt_id IS NULL));

ALTER TABLE analysis_canonical_handoff_artifacts DROP CONSTRAINT analysis_canonical_handoff_artifacts_kind_check;
ALTER TABLE analysis_canonical_handoff_artifacts ADD CONSTRAINT analysis_canonical_handoff_artifacts_kind_check
 CHECK (kind IN ('CORE','BRIDGE_RESULT','SELECTION_PROJECTION','BINDING','ASSOCIATION','MEDIA_RECEIPT','VISUAL_EVIDENCE'));
ALTER TABLE analysis_canonical_handoffs DROP CONSTRAINT analysis_canonical_handoffs_reserved_bytes_check;
ALTER TABLE analysis_canonical_handoffs ADD CONSTRAINT analysis_canonical_handoffs_reserved_bytes_check
 CHECK (reserved_bytes BETWEEN 1 AND 118620160);
