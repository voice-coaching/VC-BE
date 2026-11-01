import json
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
import analysis_diagnostics as d

class AnalysisDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        self.backend = patch.object(d,'backend_readiness',return_value={})
        self.backend.start()
        self.addCleanup(self.backend.stop)

    def test_v2_requires_exact_current_pins(self):
        audio,video={'audio':'a'},{'video':'v'}
        body={'contractVersion':'voice-coaching.canonical-handoff.v2','executorConfigured':True,
              'schemaDigests':dict(audio)}
        with patch.object(d,'contracts',return_value=video), patch.object(d,'fetch',return_value=json.dumps(body).encode()):
            details={}
            self.assertEqual(d.worker_health(self.env(),audio,details),'READY')
            self.assertTrue(details['audioDigestsMatch'])
        body['schemaDigests']['audio']='changed'
        with patch.object(d,'contracts',return_value=video), patch.object(d,'fetch',return_value=json.dumps(body).encode()):
            self.assertEqual(d.worker_health(self.env(),audio),'WORKER_SCHEMA_MISMATCH')

    def env(self):
        env = {d.PREFIX+k:'true' for k in ('WORKER_ENABLED','ADMISSION_ENABLED','ARCHIVE_ENABLED')}
        env.update({
          'ANALYSIS_CANONICAL_EVIDENCE_JOURNAL_ENABLED':'true',
          'ANALYSIS_CANONICAL_EVIDENCE_CALLBACK_ENABLED':'true',
          'AI_ANALYSIS_TRANSPORT':'runpod_http','RUNPOD_ENDPOINT_URL':'https://worker.example',
          'AI_ANALYSIS_API_TOKEN':'private-api-token','AI_ANALYSIS_CALLBACK_TOKEN':'private-callback',
          'AI_ANALYSIS_CALLBACK_BASE_URL':'https://backend.example',
          'ANALYSIS_AUTHORIZATION_KEY_ID':'private-id','ANALYSIS_AUTHORIZATION_SIGNING_SECRET_BASE64':'private-signing',
          'ANALYSIS_CONSENT_POLICY_REVISION':'voice-policy',d.PREFIX+'SPOOL_BUDGET_BYTES':'536870912',
          d.PREFIX+'B2_REGION':'us-west-004',d.PREFIX+'B2_ENDPOINT':'https://s3.us-west-004.backblazeb2.com',
          d.PREFIX+'B2_BUCKET':'private-bucket',d.PREFIX+'B2_PREFIX':'handoff/',
          d.PREFIX+'B2_WRITER_KEY_ID':'privatekey',d.PREFIX+'B2_WRITER_APPLICATION_KEY':'private-application-key',
          d.PREFIX+'RETENTION':'INDEFINITE'})
        return env

    def test_report_never_discloses_private_configuration(self):
        expected = {'contract':'digest'}
        body = json.dumps({'contractVersion':'voice-coaching.canonical-handoff.v2',
                           'executorConfigured':True,'schemaDigests':expected}).encode()
        with patch.object(d,'contracts',return_value=expected), patch.object(d,'query',return_value={'fits':True}), patch.object(d,'fetch',return_value=body), patch.object(d.Path,'exists',return_value=False):
            report = d.inspect(self.env())
        self.assertEqual(report['reasonCodes'], [])
        for secret in ('private-', 'worker.example','backend.example','us-west-004'):
            self.assertNotIn(secret,json.dumps(report))

    def test_runpod_transport_does_not_require_redis_signing_keys(self):
        env=self.env()
        for key in ('ANALYSIS_AUTHORIZATION_KEY_ID','ANALYSIS_AUTHORIZATION_SIGNING_SECRET_BASE64','ANALYSIS_CONSENT_POLICY_REVISION'):
            del env[key]
        with patch.object(d,'contracts',return_value={}), patch.object(d,'worker_health',return_value='READY'), patch.object(d,'query',return_value={'fits':True}), patch.object(d.Path,'exists',return_value=False):
            self.assertNotIn('ANALYSIS_AUTHORIZATION_UNCONFIGURED',d.inspect(env)['reasonCodes'])

    def test_closed_worker_admission_is_not_reported_as_available(self):
        body={'contractVersion':'voice-coaching.canonical-handoff.v2','executorConfigured':True,
              'admissionEnabled':False,'schemaDigests':{}}
        with patch.object(d,'contracts',return_value={}), patch.object(d,'query',return_value={'fits':True}), patch.object(d,'fetch',return_value=json.dumps(body).encode()), patch.object(d.Path,'exists',return_value=False):
            self.assertIn('WORKER_ADMISSION_CLOSED',d.inspect(self.env())['reasonCodes'])

    def test_disabled_flags_capacity_and_worker_are_reported(self):
        env=self.env()
        env[d.PREFIX+'ADMISSION_ENABLED']='false'
        with patch.object(d,'contracts',return_value={}), patch.object(d,'worker_health',return_value='WORKER_SCHEMA_MISMATCH'), patch.object(d,'query',return_value={'fits':False}), patch.object(d.Path,'exists',return_value=True):
            report=d.inspect(env)
        for code in ('HANDOFF_ADMISSION_DISABLED','HANDOFF_CAPACITY_EXHAUSTED','DEPLOYMENT_ADMISSION_HELD','WORKER_SCHEMA_MISMATCH'):
            self.assertIn(code,report['reasonCodes'])

    def test_worker_auth_rejection_does_not_disclose_exception(self):
        with patch.object(d,'fetch',side_effect=HTTPError('https://private.example',401,'private-token',{},None)):
            self.assertEqual(d.worker_health(self.env(),{}),'WORKER_AUTH_REJECTED')

    def test_contract_executor_and_schema_failures_are_distinct(self):
        cases=[
          ({'contractVersion':'old'},'WORKER_CONTRACT_MISMATCH'),
          ({'contractVersion':'voice-coaching.canonical-handoff.v2','executorConfigured':False},'WORKER_EXECUTOR_UNCONFIGURED'),
          ({'contractVersion':'voice-coaching.canonical-handoff.v2','executorConfigured':True,'schemaDigests':{}},'WORKER_SCHEMA_MISMATCH')]
        for body,reason in cases:
            with patch.object(d,'fetch',return_value=json.dumps(body).encode()):
                self.assertEqual(d.worker_health(self.env(),{'required':'digest'}),reason)
