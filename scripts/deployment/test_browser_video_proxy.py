import unittest
from browser_video_proxy import candidate,BLOCK

class BrowserVideoProxy(unittest.TestCase):
    def source(self):
        return '''server {
    server_name ai.voice-coaching.site;
    ssl_certificate /existing/cert;
    location /direct/ { client_max_body_size 20m; }
    location / {
        client_max_body_size 64k;
        proxy_pass http://127.0.0.1:28080;
    }
}
'''

    def test_adds_only_a_browser_location_and_preserves_original_bytes(self):
        original=self.source();new=candidate(original)
        self.assertEqual(new.replace(BLOCK,''),original)
        self.assertIn('client_max_body_size 256m;',new)
        self.assertIn('proxy_set_header Authorization $http_authorization;',new)
        self.assertEqual(candidate(new),new)

    def test_unexpected_existing_browser_route_is_preserved_and_rejected(self):
        existing=self.source().replace('location / {','location /browser/v1/video-analysis-jobs {')
        with self.assertRaises(RuntimeError):candidate(existing)

    def test_unrelated_or_ambiguous_proxy_file_is_rejected(self):
        with self.assertRaises(RuntimeError):candidate(self.source().replace('ai.voice-coaching.site','other.example'))
        with self.assertRaises(RuntimeError):candidate(self.source()+self.source())
