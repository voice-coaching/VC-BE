"""Deployment IO: bounded, read-only probes and private process settings."""
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
from urllib.parse import urlsplit, parse_qs
from urllib.request import Request, build_opener, ProxyHandler, HTTPRedirectHandler

ROOT = Path('/var/lib/alpha-deploy')
PROFILE = Path('/etc/alpha-deploy/runtime.json')
HOLD = ROOT / 'analysis-admission.closed'
SERVICE = 'alpha-backend.service'

def require(condition, code):
    if not condition: raise RuntimeError(code)

def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b''): h.update(part)
    return h.hexdigest()

def trusted(path):
    path = Path(path)
    for p in (path, *path.parents):
        info = p.lstat()
        require(not stat.S_ISLNK(info.st_mode) and info.st_uid == 0 and not info.st_mode & 0o022,
                'UNTRUSTED_DEPLOYMENT_PATH')
    return path

def command(args, *, timeout=30, env=None, **kwargs):
    result = subprocess.run(args, capture_output=True, timeout=timeout, env=env, **kwargs)
    require(result.returncode == 0, 'DEPLOYMENT_COMMAND_FAILED')
    return result.stdout

def environment():
    pid = command(['systemctl', 'show', SERVICE, '-p', 'MainPID', '--value']).decode().strip()
    require(pid.isdigit() and int(pid) > 1, 'BACKEND_NOT_RUNNING')
    return {k.decode(): v.decode() for k, v in
            (x.split(b'=', 1) for x in Path('/proc', pid, 'environ').read_bytes().split(b'\0') if b'=' in x)}

def query(env, sql):
    url = urlsplit(env['DB_URL'].removeprefix('jdbc:'))
    require(url.scheme in ('postgres', 'postgresql'), 'DATABASE_SCHEME')
    child = dict(PATH='/usr/bin:/bin', LANG='C.UTF-8', PGHOST=url.hostname,
                 PGPORT=str(url.port or 5432), PGDATABASE=url.path.lstrip('/'),
                 PGUSER=env['DB_USERNAME'], PGPASSWORD=env['DB_PASSWORD'], PGCONNECT_TIMEOUT='5',
                 PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=5000')
    ssl = parse_qs(url.query).get('sslmode')
    if ssl: child['PGSSLMODE'] = ssl[0]
    return json.loads(command(['psql', '-X', '-w', '-q', '-A', '-t', '-c', sql], env=child, timeout=10))

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs): return None

def fetch(url, token=None, *, limit=131072):
    target = urlsplit(url)
    require(target.username is None and target.password is None and
            (target.scheme == 'https' or (target.scheme == 'http' and target.hostname == '127.0.0.1')),
            'DEPLOYMENT_URL_INVALID')
    headers = {'Accept-Encoding': 'identity', 'User-Agent': 'voice-coaching-deployment/1'}
    if token: headers['Authorization'] = 'Bearer ' + token
    with build_opener(ProxyHandler({}), NoRedirect()).open(Request(url, headers=headers), timeout=10) as reply:
        require(reply.status == 200 and reply.headers.get('Content-Encoding', 'identity') == 'identity', 'PROBE_HTTP_FAILED')
        raw = reply.read(limit + 1)
    require(len(raw) <= limit, 'PROBE_SIZE')
    return raw

def schemas(doc):
    rows = doc['schemaDigests']
    mapped = rows if isinstance(rows, dict) else {r['file']: r['sha256'] for r in rows}
    require(len(rows) == len(mapped) == 9, 'SCHEMA_SET_INVALID')
    return mapped

def atomic(path, raw, mode=0o600):
    path = Path(path)
    temporary = path.with_name('.' + path.name + '.pending')
    fd = os.open(temporary, os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, mode)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(raw); stream.flush(); os.fsync(stream.fileno())
    os.replace(temporary, path)
    parent = os.open(path.parent, os.O_DIRECTORY)
    try: os.fsync(parent)
    finally: os.close(parent)

def record(path, doc):
    atomic(path, (json.dumps(doc, sort_keys=True) + '\n').encode())
