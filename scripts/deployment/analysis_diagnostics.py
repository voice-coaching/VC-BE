"""Read-only analysis diagnostics. Emit fixed codes and booleans only."""
import hashlib
import json
import re
import zipfile
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from common import HOLD, environment, fetch, query

SCHEMAS = ('runpod_http_control_v1.schema.json', 'runpod_http_control_v1_2.schema.json',
 'runpod_canonical_parent.schema.json', 'runpod_canonical_input.schema.json',
 'runpod_canonical_output.schema.json', 'runpod_canonical_journal_v1.schema.json',
 'runpod_analysis_request_v3.schema.json', 'runpod_result_v5.schema.json',
 'runpod_canonical_handoff_v1.schema.json')
PREFIX = 'ANALYSIS_CANONICAL_HANDOFF_'

def contracts():
    with zipfile.ZipFile('/opt/alpha/app.jar') as jar:
        return {name: hashlib.sha256(jar.read('BOOT-INF/classes/contracts/' + name)).hexdigest()
                for name in SCHEMAS}

def writer_configured(env):
    def value(name): return env.get(PREFIX + 'B2_' + name, '')
    region, prefix, key = value('REGION'), value('PREFIX'), value('WRITER_APPLICATION_KEY')
    return bool(re.fullmatch('[a-z]{2}-[a-z]+-[0-9]{3}', region)
        and value('ENDPOINT') == 'https://s3.' + region + '.backblazeb2.com'
        and re.fullmatch('[A-Za-z0-9][A-Za-z0-9-]{4,48}[A-Za-z0-9]', value('BUCKET'))
        and len(prefix) <= 800 and re.fullmatch('[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*/', prefix)
        and re.fullmatch('[A-Za-z0-9]+', value('WRITER_KEY_ID'))
        and 0 < len(key) <= 512 and all(32 < ord(c) < 127 for c in key)
        and env.get(PREFIX + 'RETENTION') == 'INDEFINITE')

def worker_health(env, expected):
    target = urlsplit(env.get('RUNPOD_ENDPOINT_URL', '').rstrip('/'))
    if target.scheme != 'https' or not target.hostname or target.username or target.password or target.query or target.fragment:
        return 'WORKER_ENDPOINT_INVALID'
    if not env.get('AI_ANALYSIS_API_TOKEN'): return 'WORKER_AUTH_UNCONFIGURED'
    try:
        raw = fetch(target.geturl() + '/health/handoff', env['AI_ANALYSIS_API_TOKEN'], limit=65536)
        doc = json.loads(raw)
        if doc.get('contractVersion') != 'voice-coaching.canonical-handoff.v1': return 'WORKER_CONTRACT_MISMATCH'
        if doc.get('executorConfigured') is not True: return 'WORKER_EXECUTOR_UNCONFIGURED'
        if doc.get('schemaDigests') != expected: return 'WORKER_SCHEMA_MISMATCH'
        return 'READY'
    except HTTPError as error:
        return {401:'WORKER_AUTH_REJECTED', 403:'WORKER_AUTH_REJECTED',
                404:'WORKER_HEALTH_ENDPOINT_NOT_FOUND', 503:'WORKER_HEALTH_UNAVAILABLE'}.get(error.code, 'WORKER_HEALTH_HTTP_ERROR')
    except (TimeoutError, URLError): return 'WORKER_UNREACHABLE'
    except Exception: return 'WORKER_HEALTH_RESPONSE_INVALID'

def inspect(env):
    reasons, checks = [], {}
    for key, code in (
        (PREFIX+'WORKER_ENABLED', 'HANDOFF_WORKER_DISABLED'),
        (PREFIX+'ADMISSION_ENABLED', 'HANDOFF_ADMISSION_DISABLED'),
        (PREFIX+'ARCHIVE_ENABLED', 'HANDOFF_ARCHIVE_DISABLED'),
        ('ANALYSIS_CANONICAL_EVIDENCE_JOURNAL_ENABLED', 'JOURNAL_DISABLED'),
        ('ANALYSIS_CANONICAL_EVIDENCE_CALLBACK_ENABLED', 'CALLBACK_DISABLED')):
        checks[code] = env.get(key) == 'true'
        if not checks[code]: reasons.append(code)
    try:
        if Path(env.get('ANALYSIS_DEPLOYMENT_HOLD_FILE', str(HOLD))).exists():
            reasons.append('DEPLOYMENT_ADMISSION_HELD')
    except OSError: reasons.append('DEPLOYMENT_HOLD_UNKNOWN')
    if env.get('AI_ANALYSIS_TRANSPORT') != 'runpod_http': reasons.append('ANALYSIS_TRANSPORT_UNCONFIGURED')
    if not all(env.get(k) for k in ('RUNPOD_ENDPOINT_URL','AI_ANALYSIS_API_TOKEN','AI_ANALYSIS_CALLBACK_TOKEN','AI_ANALYSIS_CALLBACK_BASE_URL')):
        reasons.append('RUNPOD_CONFIGURATION_INCOMPLETE')
    if not all(env.get(k) for k in ('ANALYSIS_AUTHORIZATION_KEY_ID','ANALYSIS_AUTHORIZATION_SIGNING_SECRET_BASE64','ANALYSIS_CONSENT_POLICY_REVISION')):
        reasons.append('ANALYSIS_AUTHORIZATION_UNCONFIGURED')
    if not writer_configured(env): reasons.append('ARCHIVE_WRITER_UNCONFIGURED')
    try:
        budget = int(env.get(PREFIX+'SPOOL_BUDGET_BYTES', '0'))
        if not 85065728 <= budget <= 16*1024**3: reasons.append('HANDOFF_BUDGET_UNCONFIGURED')
        else:
            sql = """SELECT json_build_object('fits',
              (SELECT COALESCE(SUM(reserved_bytes),0) FROM analysis_canonical_handoffs) +
              (SELECT COALESCE(SUM(CASE WHEN e.result_schema_version='voice-coaching.runpod-analysis-result.v6'
                THEN 118620160 ELSE 85065728 END),0)
              FROM analysis_canonical_executions e JOIN analysis_results a ON a.active_execution_id=e.execution_id::text
              WHERE e.result_schema_version IN ('voice-coaching.runpod-analysis-result.v5','voice-coaching.runpod-analysis-result.v6')
              AND a.status IN ('PENDING','PROCESSING')
              AND NOT EXISTS(SELECT 1 FROM analysis_canonical_handoffs h WHERE h.execution_id=e.execution_id))
              <= %d)""" % (budget-85065728)
            if query(env, sql).get('fits') is not True: reasons.append('HANDOFF_CAPACITY_EXHAUSTED')
    except ValueError: reasons.append('HANDOFF_BUDGET_UNCONFIGURED')
    except Exception: reasons.append('READINESS_DATABASE_UNAVAILABLE')
    try: worker = worker_health(env, contracts())
    except Exception: worker = 'BACKEND_CONTRACT_UNAVAILABLE'
    if worker != 'READY': reasons.append(worker)
    return {'kind':'ANALYSIS_READ_ONLY_DIAGNOSTICS','source':'PROCESS_ENVIRONMENT',
            'workerHealth':worker,'checks':checks,'reasonCodes':reasons}

if __name__ == '__main__':
    try: report = inspect(environment())
    except Exception: report = {'kind':'ANALYSIS_READ_ONLY_DIAGNOSTICS','reasonCodes':['DIAGNOSTICS_UNAVAILABLE']}
    print(json.dumps(report, sort_keys=True, separators=(',',':')))
