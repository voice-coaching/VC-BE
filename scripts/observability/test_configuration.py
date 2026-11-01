import json,unittest
from pathlib import Path
from configuration import replace_backend_processor,dashboard,concrete_queries,dashboard_provider

class ConfigurationTests(unittest.TestCase):
    def test_preserves_existing_sources_positions_nginx_and_endpoint(self):
        before='local.file_match "backend" { targets = [] }\n'
        old='loki.process "backend" { stage.regex { expression = "[{}]" } forward_to = [loki.write.local.receiver] }'
        after='\nloki.write "local" { endpoint { url = "http://loki:3100/loki/api/v1/push" } }'
        fragment='loki.process "backend" { stage.structured_metadata { values = { request_id = "" } } }'
        actual=replace_backend_processor(before+old+after,fragment)
        self.assertEqual(actual,before+fragment+after)
        self.assertEqual(replace_backend_processor(actual,fragment),actual)
        actual_fragment=Path(__file__).with_name('backend-process.alloy').read_text(encoding='utf-8')
        once=replace_backend_processor(before+old+after,actual_fragment)
        self.assertEqual(replace_backend_processor(once,actual_fragment),once)
    def test_missing_duplicate_or_truncated_processor_is_rejected(self):
        fragment='loki.process "backend" { request_id = "" }'
        for source in ('loki.process "other" {}','loki.process "backend" {} loki.process "backend" {}','loki.process "backend" {'):
            with self.assertRaises(ValueError):replace_backend_processor(source,fragment)
    def test_request_ids_are_not_stream_labels_and_metric_queries_drop_high_cardinality_fields(self):
        doc=dashboard('existing-loki');self.assertEqual(doc['uid'],'vc-be-request-logs')
        self.assertEqual(len(doc['panels']),8)
        for kind,query in concrete_queries(doc):
            self.assertNotIn('${',query)
            self.assertNotIn('request_id=',query.split('}')[0])
            if kind=='stat':self.assertIn('| keep http_status [15m]',query)
        self.assertEqual(json.loads(json.dumps(doc)),doc)
        self.assertIn('disableDeletion: true',dashboard_provider())
    def test_untrusted_datasource_is_rejected(self):
        for value in ('','../private','x"} malicious'):
            with self.assertRaises(ValueError):dashboard(value)

if __name__=='__main__':unittest.main()
