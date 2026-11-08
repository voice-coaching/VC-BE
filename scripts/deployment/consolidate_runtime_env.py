"""Consolidate the running Backend environment on-host; only counts/booleans leave the host."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import shlex
import stat
import subprocess
import tempfile
import time
import uuid

from common import ROOT, HOLD, SERVICE, environment, fetch, trusted
from deployment_lock import exclusive
from probes import drain
from runtime_environment import (ENV_FILE, DROPIN, KEY, EnvironmentError, require,
                                 serialize, parse, read_private, new_file, dropin_bytes, validate_managed)


def command(args, timeout=30):
    try:
        result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                timeout=timeout, env={'PATH': '/usr/bin:/bin', 'LANG': 'C.UTF-8'})
    except (OSError, subprocess.TimeoutExpired):
        raise EnvironmentError('ENV_COMMAND_FAILED') from None
    require(result.returncode == 0, 'ENV_COMMAND_FAILED')
    return result.stdout


def properties():
    return {key: command(['systemctl', 'show', SERVICE, '-p', key, '--value']).decode().strip()
            for key in ('MainPID', 'EnvironmentFiles', 'Environment', 'FragmentPath', 'DropInPaths', 'User')}


def file_state(path, private=False):
    path = Path(path)
    require(path.is_absolute() and not path.is_symlink(), 'ENV_SOURCE_INVALID')
    info = path.stat()
    require(stat.S_ISREG(info.st_mode) and info.st_uid == 0 and not info.st_mode & 0o022,
            'ENV_SOURCE_PERMISSIONS_INVALID')
    raw = read_private(path) if private else path.read_bytes()
    require(len(raw) <= 1024 * 1024, 'ENV_SOURCE_TOO_LARGE')
    return {'raw': raw, 'mode': stat.S_IMODE(info.st_mode), 'ino': info.st_ino,
            'mtime_ns': info.st_mtime_ns, 'sha256': hashlib.sha256(raw).hexdigest()}


def snapshot():
    props = properties()
    require(props['MainPID'].isdigit() and int(props['MainPID']) > 1, 'ENV_BACKEND_NOT_RUNNING')
    paths = re.findall(r'(/[^\n]+?)\s+\(ignore_errors=(?:yes|no)\)', props['EnvironmentFiles'])
    require(paths and all('\n' not in p and ' ' not in p for p in paths), 'ENV_SOURCES_INVALID')
    require(not any('%' in p for p in paths), 'ENV_SOURCES_INVALID')
    files = {p: file_state(p, True) for p in paths}
    keys = set()
    for value in files.values():
        keys.update(re.findall(rb'(?m)^[ \t]*([A-Za-z_][A-Za-z0-9_]*)[ \t]*=', value['raw']))
    names = {k.decode('ascii') for k in keys}
    inline = {}
    try:
        for entry in shlex.split(props['Environment']):
            key, sep, value = entry.partition('=')
            require(sep and KEY.fullmatch(key), 'ENV_INLINE_INVALID')
            inline[key] = value
    except ValueError:
        raise EnvironmentError('ENV_INLINE_INVALID') from None
    names.update(inline)
    current = environment()
    require(names and names <= current.keys(), 'ENV_RUNNING_KEYS_MISSING')
    require(all(current[k] == v for k, v in inline.items()), 'ENV_PENDING_INLINE_CHANGES')
    for name in [props['FragmentPath'], *props['DropInPaths'].split()]:
        files.setdefault(name, file_state(name))
    state = file_state(ROOT / 'current.json')
    files[str(ROOT / 'current.json')] = state
    release = json.loads(state['raw'])['releaseId']
    jar = str(Path('/opt/alpha/app.jar').resolve(strict=True))
    require(jar == '/opt/alpha/releases/' + release + '.jar', 'ENV_RELEASE_DRIFT')
    return {'properties': props, 'paths': paths, 'inline': inline, 'files': files,
            'values': {k: current[k] for k in names}, 'release': release, 'jar': jar}


def unchanged(before):
    after = snapshot()
    require(before['properties'] == after['properties'] and before['jar'] == after['jar']
            and before['values'] == after['values'] and before['files'] == after['files'],
            'ENV_SOURCE_CHANGED')


VERIFY = '''import json,os,sys
expected=json.load(open(sys.argv[1],encoding="utf-8"))
equal=all(os.environ.get(k)==v for k,v in expected.items())
print(json.dumps({"equal":equal,"count":len(expected)}))
sys.exit(0 if equal else 1)
'''


def systemd_compare(directory, files, expected):
    """Use the real systemd parser in an isolated one-shot, not the Java service."""
    directory = Path(directory)
    expected_file, script = directory / ('expected-' + uuid.uuid4().hex + '.json'), directory / 'verify.py'
    new_file(expected_file, json.dumps(expected, ensure_ascii=False).encode())
    if not script.exists():
        new_file(script, VERIFY.encode())
    unit = 'alpha-env-verify-' + uuid.uuid4().hex
    args = ['systemd-run', '--quiet', '--wait', '--pipe', '--collect', '--unit=' + unit,
            '-p', 'PrivateNetwork=yes', '-p', 'NoNewPrivileges=yes', '-p', 'User=root']
    for path in files:
        args += ['-p', 'EnvironmentFile=' + str(path)]
    raw = command([*args, '/usr/bin/python3', '-I', str(script), str(expected_file)], timeout=30)
    try:
        result = json.loads(raw)
    except ValueError:
        raise EnvironmentError('ENV_SYSTEMD_VERIFICATION_FAILED') from None
    require(result == {'equal': True, 'count': len(expected)}, 'ENV_SYSTEMD_VERIFICATION_FAILED')


def check_snapshot(before, work):
    candidate = Path(work) / 'candidate.env'
    new_file(candidate, serialize(before['values']))
    require(parse(candidate.read_bytes()) == before['values'], 'ENV_SERIALIZER_MISMATCH')
    inline = Path(work) / 'inline.env'
    new_file(inline, serialize(before['inline']))
    # Files override Environment= in systemd; inline is therefore loaded first.
    systemd_compare(work, [inline, *before['paths']], before['values'])
    systemd_compare(work, [candidate], before['values'])
    unchanged(before)
    return candidate


def healthy():
    try:
        return json.loads(fetch('http://127.0.0.1:9091/internal/actuator/health')).get('status') == 'UP'
    except Exception:
        return False


def wait_healthy(timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if healthy():
            return
        time.sleep(1)
    raise EnvironmentError('ENV_BACKEND_HEALTH_FAILED')


@contextmanager
def hold_and_drain(timeout=120):
    require(not HOLD.exists() and not HOLD.is_symlink(), 'ENV_ADMISSION_ALREADY_HELD')
    marker = ('env-consolidation-' + uuid.uuid4().hex).encode()
    new_file(HOLD, marker)
    preserve_hold = False
    try:
        deadline = time.monotonic() + timeout
        while True:
            counts = drain(environment())
            if all(value == 0 for value in counts.values()):
                break
            require(time.monotonic() < deadline, 'ENV_ANALYSES_NOT_DRAINED')
            print(json.dumps({'stage': 'WAITING_FOR_EXISTING_ANALYSES', 'counts': counts}), flush=True)
            time.sleep(3)
        yield
    except EnvironmentError as error:
        preserve_hold = str(error) == 'ENV_ROLLBACK_REQUIRES_OPERATOR'
        raise
    finally:
        if not preserve_hold and HOLD.exists() and not HOLD.is_symlink() and HOLD.read_bytes() == marker:
            HOLD.unlink()


def backend_env_equal(expected):
    current = environment()
    return all(current.get(k) == v for k, v in expected.items())


def apply_snapshot(before, backup):
    backup = Path(backup)
    candidate = check_snapshot(before, backup)
    require(healthy(), 'ENV_BACKEND_NOT_HEALTHY')
    require(not ENV_FILE.exists() and not ENV_FILE.is_symlink(), 'ENV_TARGET_ALREADY_EXISTS')
    require(not DROPIN.exists() and not DROPIN.is_symlink(), 'ENV_OVERRIDE_ALREADY_EXISTS')
    parent = ENV_FILE.parent
    info = parent.lstat()
    account = pwd.getpwnam(before['properties']['User'] or 'root')
    require(stat.S_ISDIR(info.st_mode) and not parent.is_symlink()
            and info.st_uid in (0, account.pw_uid) and not info.st_mode & 0o022, 'ENV_DIRECTORY_INVALID')
    # Do not change legacy inputs. They remain private rollback copies and are reset by the final override.
    originals = backup / 'originals'
    originals.mkdir(mode=0o700)
    for index, state in enumerate(before['files'].values()):
        new_file(originals / str(index), state['raw'])
    metadata = {'release': before['release'], 'source_paths': list(before['files']),
                'variables': sorted(before['values']), 'variable_count': len(before['values'])}
    new_file(backup / 'manifest.json', json.dumps(metadata).encode())
    created_env = created_override = False
    with hold_and_drain():
        unchanged(before)
        try:
            new_file(ENV_FILE, candidate.read_bytes())
            created_env = True
            new_file(DROPIN, dropin_bytes(), 0o644)
            created_override = True
            command(['systemctl', 'daemon-reload'])
            props = properties()
            require(props['EnvironmentFiles'] == str(ENV_FILE) + ' (ignore_errors=no)'
                    and not props['Environment'], 'ENV_ACTIVE_INPUTS_INVALID')
            require(read_private(ENV_FILE) == candidate.read_bytes(), 'ENV_TARGET_CHANGED')
            print(json.dumps({'stage': 'RESTARTING_WITH_CONSOLIDATED_ENV', 'variables': len(before['values'])}), flush=True)
            command(['systemctl', 'restart', SERVICE], timeout=120)
            wait_healthy()
            require(backend_env_equal(before['values']), 'ENV_RUNNING_VALUES_DIFFER')
            require(str(Path('/opt/alpha/app.jar').resolve()) == before['jar'], 'ENV_RELEASE_CHANGED')
            result = {'stage': 'APPLIED', 'envFile': str(ENV_FILE), 'variables': len(before['values']),
                      'allValuesPreserved': True, 'systemdParserVerified': True, 'health': 'UP',
                      'releaseUnchanged': True, 'backupDirectory': str(backup), 'secretValuesExported': False}
            new_file(backup / 'result.json', json.dumps(result).encode())
            return result
        except Exception:
            try:
                if created_override:
                    require(not DROPIN.is_symlink() and DROPIN.read_bytes() == dropin_bytes(), 'ENV_OVERRIDE_CHANGED')
                    DROPIN.unlink()
                    command(['systemctl', 'daemon-reload'])
                    command(['systemctl', 'restart', SERVICE], timeout=120)
                    wait_healthy()
                    require(backend_env_equal(before['values']), 'ENV_ROLLBACK_VALUES_DIFFER')
            except Exception:
                raise EnvironmentError('ENV_ROLLBACK_REQUIRES_OPERATOR') from None
            if created_env and not ENV_FILE.is_symlink() and ENV_FILE.read_bytes() == candidate.read_bytes():
                ENV_FILE.unlink()
            raise EnvironmentError('ENV_APPLY_FAILED_ROLLED_BACK' if created_override else 'ENV_FAILED_BEFORE_ACTIVATION') from None


def run(apply=False):
    require(os.geteuid() == 0, 'ENV_ROOT_REQUIRED')
    with exclusive():
        if ENV_FILE.exists() and DROPIN.exists():
            values = validate_managed()
            props = properties()
            require(props['EnvironmentFiles'] == str(ENV_FILE) + ' (ignore_errors=no)'
                    and not props['Environment'] and backend_env_equal(values) and healthy(), 'ENV_MANAGED_STATE_INVALID')
            return {'stage': 'ALREADY_CONFIGURED', 'envFile': str(ENV_FILE), 'variables': len(values),
                    'allValuesPreserved': True, 'health': 'UP', 'secretValuesExported': False}
        before = snapshot()
        backups = ROOT / 'backups'
        backups.mkdir(mode=0o700, exist_ok=True)
        trusted(backups)
        if not apply:
            with tempfile.TemporaryDirectory(prefix='env-check-', dir=backups) as work:
                check_snapshot(before, work)
            return {'stage': 'CHECKED', 'variables': len(before['values']), 'sourceEnvFiles': len(before['paths']),
                    'allValuesPreserved': True, 'systemdParserVerified': True, 'serviceRestarted': False,
                    'secretValuesExported': False}
        backup = backups / ('runtime-env-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8])
        backup.mkdir(mode=0o700)
        return apply_snapshot(before, backup)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    try:
        print(json.dumps(run(args.apply)))
    except Exception as error:
        code = str(error) if isinstance(error, EnvironmentError) else 'ENV_OPERATION_FAILED'
        print(json.dumps({'errorCode': code, 'secretValuesExported': False}))
        raise SystemExit(1)


if __name__ == '__main__':
    main()
