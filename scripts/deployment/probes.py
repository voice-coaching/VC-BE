"""Backend-only deployment probes with database drain and local health checks."""
import json
from common import fetch, query, require

def backend_health():
    state = json.loads(fetch('http://127.0.0.1:9091/internal/actuator/health'))
    require(state.get('status') == 'UP', 'BACKEND_HEALTH_NOT_READY')

def drain(env):
    counts = query(env, '''SELECT json_build_object(
      'analyses',(SELECT count(*) FROM analysis_results a WHERE status IN ('PENDING','PROCESSING')
          AND NOT (status='PENDING' AND active_request_event_id IS NULL AND active_execution_id IS NULL
              AND analysis_profile='LEGACY_SEUNGUN_V3' AND expected_result_schema_version IS NULL
              AND NOT EXISTS (SELECT 1 FROM analysis_request_outbox o WHERE o.analysis_id=a.id))),
      'dispatch',(SELECT count(*) FROM analysis_request_outbox WHERE status='PENDING'),
      'cancellation',(SELECT count(*) FROM analysis_cancellation_outbox WHERE status='PENDING'),
      'receipts',(SELECT count(*) FROM analysis_evidence_receipts WHERE status IN ('PENDING','VERIFYING')),
      'callbacks',(SELECT count(*) FROM analysis_canonical_callback_inbox WHERE status IN ('PENDING','VERIFYING','VERIFIED')),
      'apply',(SELECT count(*) FROM analysis_canonical_callback_apply WHERE state IN ('PENDING','APPLYING')),
      'effects',(SELECT count(*) FROM analysis_canonical_result_effects WHERE completed_at IS NULL),
      'uploads',(SELECT count(*) FROM analysis_canonical_upload_journal u JOIN analysis_results a
          ON a.active_execution_id=u.execution_id::text WHERE a.status IN ('PENDING','PROCESSING') AND u.state<>'VERIFIED'),
      'journals',(SELECT count(*) FROM analysis_canonical_journals j JOIN analysis_results a
          ON a.active_execution_id=j.execution_id::text WHERE a.status IN ('PENDING','PROCESSING')),
      'leases',(SELECT count(*) FROM analysis_results WHERE claim_expires_at>CURRENT_TIMESTAMP),
      'transactions',(SELECT count(DISTINCT pid) FROM pg_locks
          WHERE relation='analysis_results'::regclass AND pid<>pg_backend_pid()
          AND mode IN ('RowShareLock','RowExclusiveLock')),
      'ack',(SELECT count(*) FROM analysis_canonical_results r WHERE NOT EXISTS
            (SELECT 1 FROM analysis_canonical_ack_journal a WHERE a.event_id=r.event_id))
    )''')
    require(all(type(v) is int and v >= 0 for v in counts.values()), 'DRAIN_INVALID')
    return counts
