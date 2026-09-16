ALTER TABLE analysis_results ADD COLUMN execution_deadline_at TIMESTAMPTZ;

UPDATE analysis_results AS a
SET execution_deadline_at = (o.payload::jsonb ->> 'deadlineAt')::timestamptz
FROM analysis_request_outbox AS o
WHERE o.transport = 'RUNPOD_HTTP'
  AND o.analysis_id = a.id
  AND o.execution_id = a.active_execution_id;

CREATE INDEX idx_analysis_http_deadline ON analysis_results(execution_deadline_at)
WHERE active_execution_id IS NOT NULL AND status IN ('PENDING', 'PROCESSING');
