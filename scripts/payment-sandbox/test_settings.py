import unittest
from pathlib import Path
from bootstrap_private import checked_settings
from resource_guard import stop_needed

class SettingsIsolationTest(unittest.TestCase):
    def setUp(self):
        self.template = Path(__file__).with_name('backend.env.example').read_text()
        self.credentials = dict(PORTONE_STORE_ID='test-store', PORTONE_TEST_CHANNEL_KEY='test-channel',
                                PORTONE_API_SECRET='private-api-fixture', PORTONE_WEBHOOK_SECRET='private-webhook-fixture')

    def test_new_credentials_fill_template_without_production_identity(self):
        result = checked_settings(self.template, self.credentials, 'new-test-db-password', 'new-test-jwt-secret')
        self.assertEqual(result['DB_PASSWORD'], 'new-test-db-password')
        self.assertEqual(result['JWT_SECRET'], 'new-test-jwt-secret')
        self.assertNotIn('AWS_ACCESS_KEY_ID', result)
        self.assertNotIn('GOOGLE_APPLICATION_CREDENTIALS', result)
        self.assertNotIn('REDIS_PASSWORD', result)

    def test_production_profile_database_issuer_or_bind_cannot_be_substituted(self):
        substitutions = [('SPRING_PROFILES_ACTIVE=payment-test', 'SPRING_PROFILES_ACTIVE=prod'),
                         ('127.0.0.1:15435/payment_sandbox', 'production.invalid:5432/production'),
                         ('JWT_ISSUER=speakai-payment-sandbox', 'JWT_ISSUER=voice-coaching'),
                         ('SERVER_ADDRESS=127.0.0.1', 'SERVER_ADDRESS=0.0.0.0'),
                         ('PAYMENT_TEST_ALLOWED_USER_IDS=1', 'PAYMENT_TEST_ALLOWED_USER_IDS=')]
        for old, new in substitutions:
            with self.subTest(setting=old), self.assertRaises(RuntimeError):
                checked_settings(self.template.replace(old, new), self.credentials, 'db', 'jwt')

    def test_missing_webhook_secret_and_newline_injection_are_rejected(self):
        for replacement in ['', 'secret\nSPRING_PROFILES_ACTIVE=prod', 'secret\0payload']:
            with self.subTest(kind=bool(replacement)), self.assertRaises(RuntimeError):
                checked_settings(self.template, {**self.credentials, 'PORTONE_WEBHOOK_SECRET': replacement}, 'db', 'jwt')

    def test_guard_preserves_headroom_and_stops_failed_test_backend(self):
        self.assertFalse(stop_needed(128, 512 * 1024 * 1024, False))
        self.assertTrue(stop_needed(127, 1024 * 1024 * 1024, False))
        self.assertTrue(stop_needed(256, 511 * 1024 * 1024, False))
        self.assertTrue(stop_needed(256, 1024 * 1024 * 1024, True))

    def test_cancel_route_is_allowlisted_only_with_exact_test_order_shape(self):
        import re
        config = Path(__file__).with_name('nginx-http.conf').read_text()
        route = r'^POST:/payment-test/api/payment-orders/test[a-f0-9]{32}/cancel$'
        self.assertIn(route, config)
        self.assertIsNotNone(re.fullmatch(route, 'POST:/payment-test/api/payment-orders/test' + 'a' * 32 + '/cancel'))
        for request in ['GET:/payment-test/api/payment-orders/test' + 'a' * 32 + '/cancel',
                        'POST:/payment-test/api/payment-orders/live123/cancel',
                        'POST:/api/payment-orders/test' + 'a' * 32 + '/cancel']:
            self.assertIsNone(re.fullmatch(route, request))

if __name__ == '__main__':
    unittest.main()
