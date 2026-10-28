"""Validate the private instance and a loopback proxy before publishing /payment-test/."""
import base64
from datetime import datetime, timezone
import fcntl
import hashlib
import hmac
import http.client
import json
import os
from pathlib import Path
import secrets
import socket
import ssl
import subprocess
import time
import uuid
from bootstrap_private import CONFIG, SERVICE, available_mib, command, private_write, production_healthy, require

PREFIX = '/payment-test'
DOMAIN = 'api.voice-coaching.site'

class LoopbackTLS(http.client.HTTPSConnection):
    def connect(self):
        self.sock = ssl.create_default_context().wrap_socket(
            socket.create_connection(('127.0.0.1', 443), timeout=20), server_hostname=DOMAIN)

def connection_call(port, path, method='GET', body=None, token=None, headers=None, tls=False):
    connection = LoopbackTLS(DOMAIN, timeout=20) if tls else http.client.HTTPConnection('127.0.0.1', port, timeout=20)
    merged = {'Host': DOMAIN, 'Content-Type': 'application/json', **(headers or {})}
    if token:
        merged['Authorization'] = 'Bearer ' + token
    raw = body if isinstance(body, bytes) else None if body is None else json.dumps(body).encode()
    try:
        connection.request(method, path, body=raw, headers=merged)
        reply = connection.getresponse()
        content = reply.read(1024 * 1024 + 1)
        require(len(content) <= 1024 * 1024, 'RESPONSE_TOO_LARGE')
        return reply.status, content, dict((k.lower(), v) for k, v in reply.getheaders())
    finally:
        connection.close()

def decoded(reply):
    return json.loads(reply[1])

def settings():
    return {key: json.loads(value) for key, value in (line.split('=', 1) for line in (CONFIG / 'backend.env').read_text().splitlines())}

def private_smoke():
    env = settings()
    call = lambda path, **kwargs: connection_call(18180, path, **kwargs)
    account_file = CONFIG / 'team-account.json'
    if not account_file.exists():
        account = {'email': 'payment-team-' + secrets.token_hex(6) + '@example.invalid', 'password': 'Test1!' + secrets.token_urlsafe(32)}
        signup = call('/api/auth/signup', method='POST', body={**account, 'nickname': '결제테스트', 'termsAgreed': True, 'privacyAgreed': True})
        require(signup[0] == 200 and decoded(signup)['data']['userId'] == 1, 'TEST_SIGNUP_FAILED')
        private_write(account_file, json.dumps(account))
    account = json.loads(account_file.read_text())
    login = call('/api/auth/login', method='POST', body=account)
    require(login[0] == 200, 'PRIVATE_LOGIN_FAILED')
    token = decoded(login)['data']['accessToken']
    require(call('/api/users/me/payment-orders')[0] == 401, 'PRIVATE_AUTH_BYPASS')
    require(call('/api/users/me', token=token)[0] == 200, 'PRIVATE_PROFILE_FAILED')
    order = call('/api/payment-orders', method='POST', token=token, headers={'Idempotency-Key': str(uuid.uuid4())})
    require(order[0] == 200 and decoded(order)['data']['totalAmount'] == 15000, 'PRIVATE_ORDER_FAILED')
    order_id = decoded(order)['data']['orderId']
    unpaid = call('/api/payment-orders/' + order_id + '/verify', method='POST', token=token)
    require(unpaid[0] == 409 and decoded(unpaid).get('code') == 'PAYMENT_NOT_COMPLETED', 'UNPAID_VERIFICATION_FAILED')
    history = call('/api/users/me/payment-orders', token=token)
    require(history[0] == 200 and decoded(history)['data']['totalElements'] >= 1, 'PRIVATE_HISTORY_FAILED')
    require(connection_call(8080, '/api/users/me', token=token)[0] == 401, 'CROSS_ENVIRONMENT_TOKEN_ACCEPTED')
    body = json.dumps({'type': 'Transaction.Paid', 'timestamp': datetime.now(timezone.utc).isoformat(),
                      'data': {'storeId': env['PORTONE_STORE_ID'], 'paymentId': 'test' + '0' * 32,
                               'transactionId': 'synthetic-validation'}}).encode()
    event_id, timestamp = 'msg_' + secrets.token_hex(16), str(int(time.time()))
    secret = base64.b64decode(env['PORTONE_WEBHOOK_SECRET'].removeprefix('whsec_'))
    signature = base64.b64encode(hmac.new(secret, event_id.encode() + b'.' + timestamp.encode() + b'.' + body, hashlib.sha256).digest()).decode()
    headers = {'webhook-id': event_id, 'webhook-timestamp': timestamp, 'webhook-signature': 'v1,' + signature}
    require(call('/api/payments/webhooks/portone', method='POST', body=body, headers=headers)[0] == 200, 'SIGNED_WEBHOOK_FAILED')
    require(call('/api/payments/webhooks/portone', method='POST', body=body, headers={**headers, 'webhook-signature': 'v1,invalid'})[0] == 401, 'INVALID_WEBHOOK_ACCEPTED')
    print(json.dumps({'stage': 'PRIVATE_SMOKE_PASSED', 'real_payment_submitted': False}), flush=True)
    return account

def edge_smoke(call, account):
    login = call(PREFIX + '/api/auth/login', method='POST', body=account)
    require(login[0] == 200, 'EDGE_LOGIN_FAILED')
    token = decoded(login)['data']['accessToken']
    cookie = login[2].get('set-cookie', '')
    require('Path=/payment-test/api/auth' in cookie and 'HttpOnly' in cookie and 'Secure' in cookie, 'COOKIE_ISOLATION_FAILED')
    refresh = call(PREFIX + '/api/auth/token/refresh', method='POST', headers={'Cookie': cookie.split(';', 1)[0]})
    require(refresh[0] == 200, 'EDGE_REFRESH_FAILED')
    token = decoded(refresh)['data']['accessToken']
    require(call(PREFIX + '/api/users/me/payment-orders', token=token)[0] == 200, 'EDGE_HISTORY_FAILED')
    require(call(PREFIX + '/api/users/me/payment-orders')[0] == 401, 'EDGE_AUTH_BYPASS')
    blocked = [('POST', '/api/auth/signup'), ('POST', '/api/auth/social-login'), ('DELETE', '/api/users/me'),
               ('GET', '/internal/actuator/health'), ('POST', '/api/internal/ai/callback'), ('GET', '/api/training-sessions')]
    for method, path in blocked:
        require(call(PREFIX + path, method=method, body={} if method == 'POST' else None, token=token)[0] == 404, 'EDGE_ROUTE_NOT_BLOCKED')
    require(call(PREFIX + '/swagger-ui/index.html')[0] == 200, 'SWAGGER_UI_FAILED')
    doc = call(PREFIX + '/v3/api-docs')
    require(doc[0] == 200 and '/api/users/me/payment-orders' in decoded(doc).get('paths', {}), 'SWAGGER_DOCUMENT_FAILED')
    require(any(server.get('url', '').endswith(PREFIX) for server in decoded(doc).get('servers', [])), 'SWAGGER_SERVER_PREFIX_FAILED')
    config = call(PREFIX + '/v3/api-docs/swagger-config')
    require(config[0] == 200 and decoded(config).get('url', '').endswith(PREFIX + '/v3/api-docs'), 'SWAGGER_CONFIG_PREFIX_FAILED')
    cors = call(PREFIX + '/api/payment-orders', method='OPTIONS', headers={'Origin': 'https://vc-fe.vercel.app', 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'authorization,idempotency-key'})
    require(cors[0] == 200 and cors[2].get('access-control-allow-origin') == 'https://vc-fe.vercel.app', 'CORS_FAILED')
    require(call(PREFIX + '/api/payments/webhooks/portone', method='POST', body={})[0] == 401, 'EDGE_UNSIGNED_WEBHOOK_ACCEPTED')
    require(available_mib() >= 128, 'LOW_HOST_MEMORY')

def publish(release):
    require(os.geteuid() == 0, 'ROOT_REQUIRED')
    production_healthy(release)
    account = private_smoke()
    # Test the exact proxy snippets on a private port before changing the production config.
    preview = CONFIG / 'nginx.preview.conf'
    preview.write_text('user nginx; worker_processes 1; pid /run/alpha-payment-test-preview.pid;\n'
                       'error_log /var/log/nginx/error.log crit; events { worker_connections 64; }\n'
                       'http { include /etc/nginx/mime.types; include /etc/alpha-payment-test/nginx-http.conf;\n'
                       'server { listen 127.0.0.1:18181; server_name api.voice-coaching.site; include /etc/alpha-payment-test/nginx-server.conf; } }\n')
    command(['nginx', '-t', '-c', str(preview)])
    command(['nginx', '-c', str(preview)])
    try:
        edge_smoke(lambda path, **kwargs: connection_call(18181, path, **kwargs), account)
        print(json.dumps({'stage': 'LOOPBACK_PROXY_SMOKE_PASSED'}), flush=True)
    finally:
        command(['nginx', '-s', 'quit', '-c', str(preview)])
    original_path = Path('/etc/nginx/nginx.conf')
    original = original_path.read_bytes()
    text = original.decode()
    anchor = 'include /etc/nginx/snippets/alpha-analysis-maintenance.conf;'
    require(text.count(anchor) == 1 and '/etc/alpha-payment-test/' not in text, 'NGINX_CONFIG_DRIFT')
    candidate = text.replace('http {', 'http {\n    include /etc/alpha-payment-test/nginx-http.conf;', 1)
    candidate = candidate.replace(anchor, anchor + '\n        include /etc/alpha-payment-test/nginx-server.conf;', 1)
    candidate_path = CONFIG / 'nginx.candidate.conf'
    candidate_path.write_text(candidate)
    command(['nginx', '-t', '-c', str(candidate_path)])
    with Path('/var/lib/alpha-deploy/deployment.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        production_healthy(release)
        require(original_path.read_bytes() == original, 'NGINX_CHANGED_DURING_VALIDATION')
        private_write(CONFIG / 'nginx.production-before.conf', original.decode())
        try:
            original_path.write_text(candidate)
            command(['nginx', '-t'])
            command(['systemctl', 'reload', 'nginx'])
            time.sleep(2)
            edge_smoke(lambda path, **kwargs: connection_call(443, path, tls=True, **kwargs), account)
            production_healthy(release)
        except Exception:
            original_path.write_bytes(original)
            command(['nginx', '-t'])
            command(['systemctl', 'reload', 'nginx'])
            raise
    command(['systemctl', 'enable', SERVICE])
    report = {'stage': 'HTTPS_PUBLISHED', 'base_url': 'https://' + DOMAIN + PREFIX,
              'webhook_url': 'https://' + DOMAIN + PREFIX + '/api/payments/webhooks/portone',
              'available_memory_mib': available_mib(), 'production_health': 'UP',
              'synthetic_signature_verified': True, 'real_provider_delivery_verified': False,
              'real_payment_submitted': False}
    private_write(CONFIG / 'publish-report.json', json.dumps(report))
    print(json.dumps(report), flush=True)
