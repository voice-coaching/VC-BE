"""Compatibility and drain checks. No inference, job submission or DB writes."""
import hashlib
import json
from pathlib import Path
import pwd
import re
from common import command, digest, fetch, query, require, trusted

SHA = re.compile(r'[0-9a-f]{64}')

def profile(path):
    doc = json.loads(trusted(path).read_bytes())
    required = {'version', 'aiGitRevision', 'runpodBundleSha256', 'verifierBundleSha256', 'pins'}
    optional = {'schemaDigests', 'frontend'}
    allowed_fields = (required, required | {'schemaDigests'}, required | {'frontend'},
                      required | optional)
    require(set(doc) in allowed_fields, 'PROFILE_FIELDS')
    require(doc['version'] == 2 and re.fullmatch('[0-9a-f]{40}', doc['aiGitRevision']), 'PROFILE_VERSION')
    for key in ('runpodBundleSha256', 'verifierBundleSha256'):
        require(SHA.fullmatch(doc[key]), 'PROFILE_DIGEST')
    baseline = {'coreManifestSha256', 'llmManifestSha256', 'llmLockSha256',
                                'h5ManifestSha256', 'h5LockSha256', 'scoredCoreManifestSha256',
                                'scoredManifestSha256', 'scoredLockSha256'}
    native = {'nativeCoreManifestSha256', 'nativeManifestSha256', 'nativeLockSha256'}
    require(set(doc['pins']) in (baseline, baseline | native), 'PROFILE_PINS')
    require(all(SHA.fullmatch(v) for v in doc['pins'].values()), 'PROFILE_PIN_DIGEST')
    return doc

def verifier_python(doc, env):
    if 'nativeCoreManifestSha256' in doc['pins']:
        return '/opt/alpha-canonical/venvs/py312-jsonschema4251-rfc014-numpy246-native/bin/python'
    return env['ANALYSIS_CANONICAL_EVIDENCE_SEMANTIC_PYTHON']


def verifier(doc, env):
    root = trusted(Path('/opt/alpha-canonical/releases') / doc['verifierBundleSha256'])
    raw = (root / 'deployment.json').read_bytes()
    require(hashlib.sha256(raw).hexdigest() == doc['verifierBundleSha256'], 'VERIFIER_MANIFEST_HASH')
    for name, sha in json.loads(raw).items():
        p = root / name
        require(p.resolve().is_relative_to(root) and not p.is_symlink() and digest(p) == sha, 'VERIFIER_FILE_HASH')
    account = pwd.getpwnam('ec2-user')
    result = json.loads(command([verifier_python(doc, env), '-I',
        str(root / 'app/deploy/runpod/canonical_verify.py'), '--core-root', str(root / 'frozen/core'),
        '--llm-root', str(root / 'frozen/llm'), '--recordings-prefix',
        env['ANALYSIS_CANONICAL_EVIDENCE_SEMANTIC_RECORDINGS_PREFIX'], '--check-installation'],
        user=account.pw_uid, group=account.pw_gid, extra_groups=[], timeout=25,
        env={'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8'}))
    require(result.get('status') == 'READY' and result.get('handoffContractVersion') == 'voice-coaching.canonical-handoff.v1',
            'VERIFIER_NOT_READY')
    return root

def pod(env):
    # Existing Spring RunPodAnalysisProperties namespace.
    base = env['RUNPOD_ENDPOINT_URL'].rstrip('/')
    return base, env['AI_ANALYSIS_API_TOKEN']

def pod_identity(doc, env):
    base, token = pod(env)
    state = json.loads(fetch(base + '/health/deployment', token))
    require(state.get('protocol') == 'canonical-deployment-v1' and not state.get('stopping'), 'POD_DEPLOYMENT_UNAVAILABLE')
    return state

def ready(doc, env):
    # Verify support independently of the external maintenance gate.
    state = json.loads(fetch('http://127.0.0.1:8080/api/internal/ai/worker-readiness/handoff', env['AI_ANALYSIS_CALLBACK_TOKEN']))
    require(state.get('supported') is True and state.get('admissionEnabled') is True,
            'BACKEND_CANONICAL_NOT_READY')
    base, token = pod(env)
    state = json.loads(fetch(base + '/health/handoff', token))
    require(state.get('executorConfigured') is True and state.get('admissionEnabled') is True, 'POD_CANONICAL_NOT_READY')
    pod_identity(doc, env)

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
      'ack',(SELECT count(*) FROM analysis_canonical_results r WHERE r.schema_version='voice-coaching.runpod-analysis-result.v4' AND NOT EXISTS
            (SELECT 1 FROM analysis_canonical_ack_journal a WHERE a.event_id=r.event_id))
    )''')
    base, token = pod(env)
    state = json.loads(fetch(base + '/health/deployment', token))
    require(state.get('protocol') == 'canonical-deployment-v1' and type(state.get('activeJobs')) is int
            and state['activeJobs'] >= 0 and not state.get('stopping'), 'POD_DRAIN_UNKNOWN')
    counts['podJobs'] = state['activeJobs']
    require(all(type(v) is int and v >= 0 for v in counts.values()), 'DRAIN_INVALID')
    return counts
