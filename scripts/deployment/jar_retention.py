"""Keep the live JAR and fifteen rollback versions after verified healthy startup."""
import argparse
from dataclasses import dataclass
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import time
from common import ROOT, SERVICE, digest, fetch, record, require, trusted
from deployment_lock import exclusive

ROLLBACK_COUNT = 15
SHA = re.compile(r'[0-9a-f]{40}')
JAR = re.compile(r'[0-9a-f]{40}\.jar')


@dataclass(frozen=True)
class Layout:
    releases: Path = Path('/opt/alpha/releases')
    live: Path = Path('/opt/alpha/app.jar')
    state_root: Path = ROOT

    @property
    def healthy(self):
        return self.state_root / 'jar-retention' / 'healthy.json'


def identity(path):
    info = path.lstat()
    return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_mode)


def document(path):
    trusted(path)
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and not path.is_symlink() and info.st_size <= 1024 * 1024,
            'RETENTION_STATE_UNTRUSTED')
    value = json.loads(path.read_text(encoding='utf-8'))
    require(isinstance(value, dict), 'RETENTION_STATE_INVALID')
    return value


def inventory(layout):
    require(layout.releases.resolve(strict=True) == layout.releases, 'RELEASE_DIRECTORY_LINK')
    rows = {}
    for path in layout.releases.iterdir():
        if path.suffix != '.jar':
            continue
        require(stat.S_ISREG(path.lstat().st_mode) and not path.is_symlink(), 'RELEASE_JAR_NOT_REGULAR')
        rows[path.name] = {'path': path, 'identity': identity(path)}
    return rows


def release_state(layout, expected_release, expected_sha):
    require(bool(SHA.fullmatch(expected_release)) and bool(re.fullmatch('[0-9a-f]{64}', expected_sha)),
            'RETENTION_EXPECTATION_INVALID')
    state = document(layout.state_root / 'current.json')
    require(state.get('stage') in ['ACTIVATED', 'COMPLETE'], 'CURRENT_RELEASE_NOT_ACTIVATED')
    require(state.get('releaseId') == expected_release and state.get('jarSha256') == expected_sha,
            'RETENTION_EXPECTATION_CHANGED')
    target = layout.releases / (expected_release + '.jar')
    require(layout.live.resolve(strict=True) == target and not target.is_symlink()
            and digest(target) == expected_sha, 'CURRENT_RELEASE_DRIFT')
    return state


def protect_path(raw, layout, rows, protected, missing_history=None):
    path = Path(raw)
    require(path.is_absolute(), 'REFERENCE_PATH_NOT_ABSOLUTE')
    if missing_history is not None and not path.exists():
        # Earlier approved manual cleanup may have removed an archived predecessor.
        # This is informational only; the CURRENT rollback reference remains strict.
        require(path.parent == layout.releases and path.suffix == '.jar' and not path.is_symlink(),
                'REFERENCE_OUTSIDE_RELEASES')
        missing_history.add(path.name)
        return
    target = path.resolve(strict=True)
    require(target.parent == layout.releases and target.name in rows, 'REFERENCE_OUTSIDE_RELEASES')
    protected.add(target.name)


def selection(layout, rows, state, healthy, references):
    """Pure retention decision apart from reading selected rollback-state references."""
    current = state['releaseId'] + '.jar'
    protected = {name for name in rows if not JAR.fullmatch(name)}
    missing_history = set()
    protected.add(current)
    for raw in references:
        protect_path(raw, layout, rows, protected)
    versions = healthy.get('releases', [])
    require(isinstance(versions, list) and len(versions) <= 512, 'HEALTHY_LEDGER_INVALID')
    known = []
    seen = set()
    for version in versions:
        require(isinstance(version, dict) and bool(SHA.fullmatch(str(version.get('releaseId', ''))))
                and bool(re.fullmatch('[0-9a-f]{64}', str(version.get('jarSha256', ''))))
                and isinstance(version.get('confirmedAt'), str), 'HEALTHY_LEDGER_INVALID')
        require(version['releaseId'] not in seen, 'HEALTHY_LEDGER_DUPLICATE')
        seen.add(version['releaseId'])
        # Validate actual ISO timestamps rather than trusting lexicographic caller input.
        confirmed = datetime.fromisoformat(version['confirmedAt'])
        require(confirmed.tzinfo is not None, 'HEALTHY_LEDGER_TIME_INVALID')
        name = version['releaseId'] + '.jar'
        if name in rows and name != current:
            known.append((confirmed, name))
    rollback = [name for _, name in sorted(known, reverse=True)[:ROLLBACK_COUNT]]
    # Bootstrap safely: existing files have no health receipts. Fill only missing slots
    # with recent SHA-named JARs; never describe those as verified healthy versions.
    fallback = [name for name in sorted(rows, key=lambda name: (rows[name]['identity'][3], name), reverse=True)
                if JAR.fullmatch(name) and name != current and name not in rollback]
    rollback += fallback[:max(0, ROLLBACK_COUNT - len(rollback))]
    protected.update(rollback)
    for name in [current, *rollback]:
        path = layout.state_root / 'releases' / name[:-4] / 'state.json'
        if path.exists():
            saved = document(path)
            require(saved.get('releaseId') == name[:-4], 'ROLLBACK_STATE_ID_MISMATCH')
            if saved.get('previousJar'):
                protect_path(saved['previousJar'], layout, rows, protected, missing_history)
    if state.get('previousJar'):
        protect_path(state['previousJar'], layout, rows, protected)
    # Unknown legacy JARs without a release receipt are retained for manual review.
    for name in rows:
        if JAR.fullmatch(name) and not (layout.state_root / 'releases' / name[:-4] / 'state.json').exists():
            protected.add(name)
    candidates = sorted(set(rows) - protected)
    return {'preserved': sorted(protected), 'candidates': candidates,
            'missing_archived_predecessors': sorted(missing_history),
            'verified_rollback_versions': min(len(known), ROLLBACK_COUNT),
            'bootstrap_rollback_versions': len(rollback) - min(len(known), ROLLBACK_COUNT),
            'bytes': sum(rows[name]['identity'][2] for name in candidates)}


def command(args):
    return subprocess.run(args, capture_output=True, check=True, text=True, timeout=15).stdout


def system_references(layout):
    """Inspect Java commands/open files, alpha service JARs and production/test links."""
    refs = set()
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            if proc.joinpath('comm').read_text().strip() != 'java':
                continue
            args = [os.fsdecode(arg) for arg in proc.joinpath('cmdline').read_bytes().split(b'\0') if arg]
            for index, arg in enumerate(args[:-1]):
                if arg == '-jar':
                    path = Path(args[index + 1])
                    if not path.is_absolute():
                        path = proc.joinpath('cwd').resolve(strict=True) / path
                    target = path.resolve(strict=True)
                    if target.parent == layout.releases:
                        refs.add(str(target))
            for fd in proc.joinpath('fd').iterdir():
                try:
                    target = fd.resolve(strict=True)
                    if target.parent == layout.releases:
                        refs.add(str(target))
                except FileNotFoundError:
                    pass
        except (FileNotFoundError, ProcessLookupError):
            pass
    for directory in Path('/opt').iterdir():
        if directory.name.startswith('alpha') and directory.is_dir():
            for path in directory.glob('*.jar'):
                if path.is_symlink():
                    target = path.resolve(strict=True)
                    if target.parent == layout.releases:
                        refs.add(str(target))
    for line in command(['systemctl', 'list-unit-files', '--type=service', '--no-legend', '--no-pager']).splitlines():
        name = line.split()[0]
        if name.startswith('alpha-') and name.endswith('.service'):
            raw = command(['systemctl', 'show', name, '-p', 'ExecStart'])
            for value in re.findall(r'-jar\s+(/opt/[A-Za-z0-9_./-]+\.jar)', raw):
                target = Path(value).resolve(strict=True)
                if target.parent == layout.releases:
                    refs.add(str(target))
    return refs


def backend_identity():
    values = command(['systemctl', 'show', SERVICE, '-p', 'MainPID', '-p', 'ActiveState', '-p', 'SubState', '-p', 'NRestarts'])
    state = dict(line.split('=', 1) for line in values.splitlines() if '=' in line)
    require(state.get('ActiveState') == 'active' and state.get('SubState') == 'running'
            and int(state.get('MainPID', '0')) > 1, 'BACKEND_NOT_RUNNING')
    return state


def backend_health():
    require(json.loads(fetch('http://127.0.0.1:9091/internal/actuator/health')).get('status') == 'UP',
            'BACKEND_NOT_HEALTHY')


def serving_jar(layout, expected_release, service):
    expected = layout.releases / (expected_release + '.jar')
    for path in Path('/proc', service['MainPID'], 'fd').iterdir():
        try:
            if path.resolve(strict=True) == expected:
                return
        except FileNotFoundError:
            pass
    raise RuntimeError('RUNNING_JAR_NOT_EXPECTED_RELEASE')


def stable_startup(timeout=60):
    deadline = time.monotonic() + timeout
    previous, consecutive = None, 0
    while True:
        try:
            current = backend_identity()
            backend_health()
            consecutive = consecutive + 1 if current == previous else 1
            previous = current
            if consecutive >= 3:
                return current
        except Exception:
            previous, consecutive = None, 0
        require(time.monotonic() < deadline, 'HEALTHY_STARTUP_NOT_CONFIRMED')
        time.sleep(1)


def reject_legacy_deployment(expected_release):
    # Older workflow revisions may not participate in deployment.lock.
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit() or int(proc.name) == os.getpid():
            continue
        try:
            args = [os.fsdecode(arg) for arg in proc.joinpath('cmdline').read_bytes().split(b'\0') if arg]
        except (FileNotFoundError, ProcessLookupError):
            continue
        require(not any(arg.endswith('/release.py') and index + 1 < len(args)
                        and args[index + 1] in ['deploy', 'activate', 'rollback', 'resume'] for index, arg in enumerate(args)),
                'LEGACY_DEPLOYMENT_RUNNING')
        require(not any(re.fullmatch(r'/var/tmp/alpha-deploy-[0-9a-f]{40}/deploy.sh', arg) for arg in args),
                'LEGACY_DEPLOYMENT_RUNNING')
    for path in Path('/var/tmp').glob('alpha-*.jar'):
        if path.name != 'alpha-' + expected_release + '.jar':
            require(time.time() - path.stat().st_mtime > 600, 'OTHER_RELEASE_UPLOAD_IN_PROGRESS')


def state_fingerprint(layout):
    paths = [layout.state_root / 'current.json', *sorted((layout.state_root / 'releases').glob('*/state.json'))]
    return [(str(path), identity(path)) for path in paths]


def execute(layout, expected_release, expected_sha, apply=False):
    release_state(layout, expected_release, expected_sha)
    service = stable_startup()
    serving_jar(layout, expected_release, service)
    reject_legacy_deployment(expected_release)
    rows = inventory(layout)
    refs = system_references(layout)
    healthy = document(layout.healthy) if layout.healthy.exists() else {'version': 1, 'releases': []}
    require(healthy.get('version') == 1, 'HEALTHY_LEDGER_VERSION_INVALID')
    state = release_state(layout, expected_release, expected_sha)
    decision = selection(layout, rows, state, healthy, refs)
    fingerprint = state_fingerprint(layout)
    now = datetime.now(timezone.utc).isoformat()
    receipt = {'kind': 'JAR_RETENTION', 'at': now, 'releaseId': expected_release, 'rollback_count': ROLLBACK_COUNT,
               'mode': 'APPLY' if apply else 'DRY_RUN', 'preserved': decision['preserved'], 'candidates': decision['candidates'],
               'verified_rollback_versions': decision['verified_rollback_versions'], 'bootstrap_rollback_versions': decision['bootstrap_rollback_versions'],
               'missing_archived_predecessors': decision['missing_archived_predecessors'],
               'planned_bytes': decision['bytes'], 'deleted': [], 'status': 'PLANNED'}
    if not apply:
        return receipt
    ledger_dir = layout.healthy.parent
    ledger_dir.mkdir(mode=0o700, exist_ok=True)
    trusted(ledger_dir)
    current = {'releaseId': expected_release, 'jarSha256': expected_sha, 'confirmedAt': now}
    entries = [row for row in healthy['releases'] if row['releaseId'] != expected_release]
    # Validate/order all persisted entries before truncating the bounded receipt ledger.
    entries.sort(key=lambda row: datetime.fromisoformat(row['confirmedAt']), reverse=True)
    record(layout.healthy, {'version': 1, 'releases': [current, *entries][:512]})
    receipt_path = ledger_dir / ('cleanup-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '.json')
    record(receipt_path, receipt)
    directory_fd = os.open(layout.releases, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for name in decision['candidates']:
            reject_legacy_deployment(expected_release)
            require(state_fingerprint(layout) == fingerprint, 'RETENTION_RELEASE_STATE_CHANGED')
            require(layout.live.resolve(strict=True) == layout.releases / (expected_release + '.jar'), 'CURRENT_RELEASE_DRIFT')
            require(system_references(layout) == refs and backend_identity() == service, 'RETENTION_REFERENCES_CHANGED')
            backend_health()
            info = os.stat(name, dir_fd=directory_fd, follow_symlinks=False)
            actual = (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_mode)
            require(actual == rows[name]['identity'] and stat.S_ISREG(info.st_mode) and bool(JAR.fullmatch(name)),
                    'CANDIDATE_CHANGED_BEFORE_DELETE')
            receipt['pending_delete'] = name
            record(receipt_path, receipt)
            os.unlink(name, dir_fd=directory_fd)
            receipt['deleted'].append({'name': name, 'bytes': info.st_size})
            receipt.pop('pending_delete')
            record(receipt_path, receipt)
        os.fsync(directory_fd)
        require(all(identity(rows[name]['path']) == rows[name]['identity'] for name in decision['preserved']),
                'PRESERVED_JAR_CHANGED')
        release_state(layout, expected_release, expected_sha)
        require(backend_identity() == service, 'BACKEND_IDENTITY_CHANGED')
        serving_jar(layout, expected_release, service)
        backend_health()
        receipt.update(status='COMPLETE', freed_bytes=sum(row['bytes'] for row in receipt['deleted']),
                       remaining_jar_count=len(inventory(layout)), receipt=receipt_path.name)
        record(receipt_path, receipt)
        return receipt
    except Exception:
        receipt['status'] = 'STOPPED'
        record(receipt_path, receipt)
        raise
    finally:
        os.close(directory_fd)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected-release', required=True)
    parser.add_argument('--expected-jar-sha', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args(argv)
    try:
        require(os.geteuid() == 0, 'ROOT_REQUIRED')
        layout = Layout()
        trusted(layout.state_root)
        # Existing EC2 service directory is owned by the service user; the actual
        # releases directory and JARs must still be root owned and not writable.
        from release_snapshot import trusted_release_jar
        trusted_release_jar(layout.releases)
        with exclusive():
            print(json.dumps(execute(layout, args.expected_release, args.expected_jar_sha, args.apply), sort_keys=True))
        return 0
    except Exception as error:
        code = str(error)
        print(json.dumps({'kind': 'JAR_RETENTION', 'status': 'STOPPED',
                          'error': code if re.fullmatch('[A-Z_]+', code) else 'RETENTION_UNAVAILABLE'}))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
