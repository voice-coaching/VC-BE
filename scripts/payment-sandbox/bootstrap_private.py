"""Root-only initial bootstrap. Payload and generated secrets must never be logged.

Creates only the dedicated payment-test container/service; does not publish Nginx routes.
Call bootstrap(payload) over the administrator's SSH stdin, not with secret command arguments.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import time
from urllib import error, request

SERVICE = 'alpha-payment-test.service'
CONTAINER = 'alpha-payment-test-db'
ROOT = Path('/var/lib/alpha-payment-test')
CONFIG = Path('/etc/alpha-payment-test')
OPENER = request.build_opener(request.ProxyHandler({}))

def require(condition, code):
    if not condition:
        raise RuntimeError(code)

def command(args, data=None, timeout=30):
    reply = subprocess.run(args, input=data, capture_output=True, timeout=timeout)
    require(reply.returncode == 0, 'COMMAND_FAILED')
    return reply.stdout.decode().strip()

def available_mib():
    return next(int(row.split()[1]) // 1024 for row in Path('/proc/meminfo').read_text().splitlines() if row.startswith('MemAvailable:'))

def api(path, method='GET', body=None, token=None, port=18180):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = request.Request(f'http://127.0.0.1:{port}' + path, method=method, headers=headers,
                          data=None if body is None else json.dumps(body).encode())
    try:
        response = OPENER.open(req, timeout=15)
    except error.HTTPError as exc:
        response = exc
    with response:
        return response.status, json.load(response)

def production_healthy(release):
    require(Path('/opt/alpha/app.jar').resolve().name == release + '.jar', 'PRODUCTION_RELEASE_DRIFT')
    code, body = api('/internal/actuator/health', port=9091)
    require(code == 200 and body.get('status') == 'UP', 'PRODUCTION_NOT_HEALTHY')

def private_write(path, text):
    with path.open('x', encoding='utf-8') as stream:
        os.chmod(path, 0o600)
        stream.write(text)

def parse_env(text):
    values = {}
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        key, sep, value = line.partition('=')
        require(bool(sep) and re.fullmatch(r'[A-Z][A-Z0-9_]*', key), 'INVALID_ENV_TEMPLATE')
        values[key] = value
    return values

def checked_settings(template, credentials, database_password, signing_secret):
    settings = parse_env(template)
    expected = {'PORTONE_STORE_ID', 'PORTONE_TEST_CHANNEL_KEY', 'PORTONE_API_SECRET', 'PORTONE_WEBHOOK_SECRET'}
    require(set(credentials) == expected and all(credentials.values()), 'PORTONE_CREDENTIALS_REQUIRED')
    require(all('\n' not in v and '\r' not in v and '\0' not in v for v in credentials.values()), 'INVALID_CREDENTIAL_FORMAT')
    settings.update(credentials)
    settings.update(DB_PASSWORD=database_password, JWT_SECRET=signing_secret)
    require(settings.get('SPRING_PROFILES_ACTIVE') == 'payment-test', 'INVALID_TEST_PROFILE')
    require(settings.get('DB_URL') == 'jdbc:postgresql://127.0.0.1:15435/payment_sandbox', 'INVALID_TEST_DATABASE')
    require(settings.get('JWT_ISSUER') == 'speakai-payment-sandbox', 'INVALID_TEST_ISSUER')
    require(settings.get('SERVER_ADDRESS') == '127.0.0.1', 'INVALID_BIND_ADDRESS')
    require(settings.get('PAYMENT_TEST_ALLOWED_USER_IDS') == '1', 'INVALID_TEST_ALLOWLIST')
    require(not any('REPLACE_WITH_' in value for value in settings.values()), 'UNFILLED_SETTING')
    return settings

def bootstrap(payload):
    import pwd
    require(os.geteuid() == 0, 'ROOT_REQUIRED')
    release = payload['release']
    require(re.fullmatch(r'[0-9a-f]{40}', release), 'INVALID_RELEASE')
    production_healthy(release)
    require(available_mib() >= 460, 'INSUFFICIENT_INITIAL_MEMORY')
    require(not ROOT.exists() and not CONFIG.exists() and not Path('/opt/alpha-payment-test').exists(), 'TEST_ENVIRONMENT_ALREADY_EXISTS')
    for port in (15435, 18180, 19191):
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', port))
    db_password, owner_password, signing_secret = (secrets.token_urlsafe(36) for _ in range(3))
    settings = checked_settings(payload['files']['backend.env.example'], payload['credentials'], db_password, signing_secret)
    image = command(['docker', 'image', 'inspect', 'postgres:18-alpine', '--format', '{{.Id}}'])
    require(re.fullmatch(r'sha256:[0-9a-f]{64}', image), 'POSTGRES_IMAGE_REQUIRED')
    uid = int(command(['docker', 'run', '--rm', '--network', 'none', '--memory', '32m', '--pids-limit', '16', '--entrypoint', 'id', image, '-u', 'postgres']))
    gid = int(command(['docker', 'run', '--rm', '--network', 'none', '--memory', '32m', '--pids-limit', '16', '--entrypoint', 'id', image, '-g', 'postgres']))
    ROOT.mkdir(mode=0o755)
    CONFIG.mkdir(mode=0o700)
    command(['useradd', '--system', '--home-dir', str(ROOT / 'runtime'), '--no-create-home', '--shell', '/sbin/nologin', 'alpha-payment-test'])
    user = pwd.getpwnam('alpha-payment-test')
    runtime = ROOT / 'runtime'
    runtime.mkdir(mode=0o700)
    os.chown(runtime, user.pw_uid, user.pw_gid)
    pgdir = ROOT / 'postgres'
    pgdir.mkdir(mode=0o700)
    os.chown(pgdir, uid, gid)
    private_write(CONFIG / 'backend.env', ''.join(k + '=' + json.dumps(v) + '\n' for k, v in settings.items()))
    private_write(CONFIG / 'postgres.env', 'POSTGRES_USER=payment_test_owner\nPOSTGRES_DB=payment_sandbox\nPOSTGRES_PASSWORD=' + owner_password + '\nPOSTGRES_INITDB_ARGS=--auth-host=scram-sha-256 --auth-local=trust\n')
    for name in ('nginx-http.conf', 'nginx-server.conf'):
        private_write(CONFIG / name, payload['files'][name])
    unit = Path('/etc/systemd/system') / SERVICE
    private_write(unit, payload['files'][SERVICE])
    os.chmod(unit, 0o644)
    Path('/opt/alpha-payment-test').mkdir(mode=0o755)
    Path('/opt/alpha-payment-test/app.jar').symlink_to('/opt/alpha/releases/' + release + '.jar')
    command(['systemd-analyze', 'verify', str(unit)])
    started = False
    try:
        command(['docker', 'run', '-d', '--name', CONTAINER, '--pull', 'never', '--restart', 'unless-stopped',
                 '--user', 'postgres', '--memory', '128m', '--memory-swap', '128m', '--cpus', '0.25', '--pids-limit', '64',
                 '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges:true', '--read-only',
                 '--tmpfs', f'/var/run/postgresql:rw,nosuid,nodev,size=8m,uid={uid},gid={gid},mode=3775',
                 '--tmpfs', '/tmp:rw,nosuid,nodev,size=16m,mode=1777',
                 '--log-opt', 'max-size=5m', '--log-opt', 'max-file=2',
                 '-p', '127.0.0.1:15435:5432', '--mount', f'type=bind,src={pgdir},dst=/var/lib/postgresql',
                 '--env-file', str(CONFIG / 'postgres.env'), image,
                 '-c', 'shared_buffers=8MB', '-c', 'max_connections=12', '-c', 'work_mem=1MB',
                 '-c', 'maintenance_work_mem=16MB', '-c', 'max_wal_size=128MB', '-c', 'min_wal_size=32MB'])
        started = True
        for _ in range(45):
            ready = subprocess.run(['docker', 'exec', CONTAINER, 'pg_isready', '-h', '127.0.0.1', '-U', 'payment_test_owner', '-d', 'payment_sandbox'], capture_output=True).returncode == 0
            if ready:
                break
            time.sleep(2)
        else:
            raise RuntimeError('TEST_DATABASE_NOT_READY')
        sql = "CREATE ROLE payment_test_app LOGIN PASSWORD '" + db_password + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION; ALTER DATABASE payment_sandbox OWNER TO payment_test_app; GRANT ALL ON SCHEMA public TO payment_test_app;"
        command(['docker', 'exec', '-i', CONTAINER, 'psql', '-X', '-U', 'payment_test_owner', '-d', 'payment_sandbox', '-v', 'ON_ERROR_STOP=1'], data=sql.encode())
        require(available_mib() >= 380, 'INSUFFICIENT_BACKEND_MEMORY')
        command(['systemctl', 'daemon-reload'])
        command(['systemctl', 'start', SERVICE])
        print(json.dumps({'stage': 'PRIVATE_BACKEND_STARTING'}), flush=True)
        for _ in range(90):
            require(available_mib() >= 128, 'LOW_HOST_MEMORY_STOP_TEST_ONLY')
            require(command(['systemctl', 'show', SERVICE, '-p', 'NRestarts', '--value']) == '0', 'TEST_BACKEND_RESTARTED')
            try:
                code, body = api('/internal/actuator/health', port=19191)
                if code == 200 and body.get('status') == 'UP':
                    break
            except (OSError, ValueError):
                pass
            time.sleep(2)
        else:
            raise RuntimeError('TEST_BACKEND_STARTUP_TIMEOUT')
        email, password = 'payment-team-' + secrets.token_hex(6) + '@example.invalid', 'Test1!' + secrets.token_urlsafe(32)
        code, result = api('/api/auth/signup', 'POST', {'email': email, 'password': password, 'nickname': '결제테스트', 'termsAgreed': True, 'privacyAgreed': True})
        require(code == 200 and result.get('data', {}).get('userId') == 1, 'TEST_ACCOUNT_INIT_FAILED')
        private_write(CONFIG / 'team-account.json', json.dumps({'email': email, 'password': password}))
        production_healthy(release)
        for _ in range(3):
            require(available_mib() >= 128, 'LOW_HOST_MEMORY_STOP_TEST_ONLY')
            time.sleep(3)
        report = {'stage': 'PRIVATE_READY', 'release': release, 'postgres_image_id': image,
                  'available_memory_mib': available_mib(),
                  'backend_memory_bytes': int(command(['systemctl', 'show', SERVICE, '-p', 'MemoryCurrent', '--value'])),
                  'production_health': 'UP', 'published': False}
        private_write(CONFIG / 'bootstrap-report.json', json.dumps(report))
        print(json.dumps(report), flush=True)
    except Exception:
        subprocess.run(['systemctl', 'stop', SERVICE], capture_output=True)
        if started:
            subprocess.run(['docker', 'stop', CONTAINER], capture_output=True)
        raise
