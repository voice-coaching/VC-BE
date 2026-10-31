"""Root-only backend release state machine with local health and rollback."""
import argparse
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import time
from common import ROOT, HOLD, SERVICE, atomic, command, digest, environment, record, require, trusted
import probes
from release_snapshot import RELEASES, current_snapshot, trusted_release_jar

LIVE = Path('/opt/alpha/app.jar')

def gate_configured():
    expected = Path(__file__).with_name('nginx-analysis-maintenance.conf').read_bytes()
    actual = trusted('/etc/nginx/snippets/alpha-analysis-maintenance.conf')
    require(actual.read_bytes() == expected, 'MAINTENANCE_CONFIG_MISMATCH')
    rendered = command(['nginx', '-T']).decode()
    require('include /etc/nginx/snippets/alpha-analysis-maintenance.conf;' in rendered,
            'MAINTENANCE_INCLUDE_MISSING')
    # Reload the validated configuration; nginx -T alone does not prove the running config.
    command(['systemctl', 'reload', 'nginx'])

def wait_drained(env):
    deadline, consecutive = time.monotonic() + 1200, 0
    while time.monotonic() < deadline:
        counts = probes.drain(env)
        print(json.dumps({'stage': 'DRAIN', 'counts': counts}), flush=True)
        consecutive = consecutive + 1 if not any(counts.values()) else 0
        if consecutive == 2: return
        time.sleep(5)
    raise RuntimeError('DRAIN_TIMEOUT_KEEP_CURRENT_RELEASE')

def wait_ready():
    deadline, consecutive, last = time.monotonic() + 300, 0, 'NOT_READY'
    while time.monotonic() < deadline:
        try:
            probes.backend_health()
            consecutive += 1
            if consecutive == 3: return
        except Exception as error:
            consecutive = 0
            last = str(error) if re.fullmatch('[A-Z_]+', str(error)) else 'PROBE_FAILED'
        print(json.dumps({'stage': 'READINESS', 'consecutive': consecutive, 'reason': last}), flush=True)
        time.sleep(15)
    raise RuntimeError('CANONICAL_READINESS_TIMEOUT')

def check_jar(path, sha):
    require(path.is_file() and not path.is_symlink() and digest(path) == sha, 'JAR_DIGEST_MISMATCH')

def switch_jar(target):
    link = LIVE.with_name('.app.jar.pipeline-pending')
    require(not link.exists() and not link.is_symlink(), 'JAR_SWITCH_PENDING')
    link.symlink_to(target)
    os.replace(link, LIVE)

def stage(state_path, state, name):
    state['stage'] = name
    state['updatedAt'] = datetime.now(timezone.utc).isoformat()
    record(state_path, state)
    print(json.dumps({'release': state['releaseId'], 'stage': name}), flush=True)

def run(args):
    require(os.geteuid() == 0 and re.fullmatch('[0-9a-f]{40}', args.release), 'ROOT_RELEASE_REQUIRED')
    require(re.fullmatch('[0-9a-f]{64}', args.jar_sha), 'JAR_SHA_REQUIRED')
    expected_release = getattr(args, 'expected_current_release', None)
    expected_jar = getattr(args, 'expected_current_jar_sha', None)
    require((expected_release is None) == (expected_jar is None), 'EXPECTED_CURRENT_PAIR_REQUIRED')
    if expected_release is not None:
        require(args.phase == 'deploy', 'EXPECTED_CURRENT_DEPLOY_ONLY')
        require(re.fullmatch('[0-9a-f]{40}', expected_release)
                and re.fullmatch('[0-9a-f]{64}', expected_jar), 'EXPECTED_CURRENT_INVALID')
    ROOT.mkdir(mode=0o755, exist_ok=True); trusted(ROOT)
    require(ROOT.stat().st_mode & 0o777 == 0o755, 'MAINTENANCE_DIRECTORY_PERMISSIONS')
    with (ROOT / 'deployment.lock').open('a') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        if expected_release is not None:
            # Close the gap between the runner's Git policy check and activation.
            # Fail before nginx reload, admission hold, drain or service restart.
            require(current_snapshot() == {'releaseId': expected_release, 'jarSha256': expected_jar},
                    'CURRENT_RELEASE_CHANGED')
        directory = ROOT / 'releases' / args.release
        state_path = directory / 'state.json'
        effective = environment()
        target = RELEASES / (args.release + '.jar')
        incoming = Path('/var/tmp') / ('alpha-' + args.release + '.jar')
        source = target if target.exists() else incoming
        if args.phase not in ('abort', 'rollback'): check_jar(source, args.jar_sha)
        if args.phase == 'check':
            print(json.dumps({'stage': 'PREFLIGHT_READY', 'release': args.release,
                              'jarSha256': args.jar_sha})); return
        state = json.loads(state_path.read_bytes()) if state_path.exists() else None
        if state:
            require(state['jarSha256'] == args.jar_sha, 'PENDING_RELEASE_CHANGED')
            if state['stage'] == 'COMPLETE':
                require(not HOLD.exists() and LIVE.resolve() == target and digest(LIVE) == args.jar_sha,
                        'COMPLETE_RELEASE_DRIFT')
                probes.backend_health()
                print(json.dumps({'stage': 'ALREADY_COMPLETE', 'release': args.release})); return
        if args.phase in ('deploy', 'hold'):
            require(state is None and not HOLD.exists(), 'DEPLOYMENT_ALREADY_PENDING_INSPECT')
            # CI may not initiate a cross-contract transition before AI is ready.
            gate_configured()
            directory.mkdir(parents=True, mode=0o700, exist_ok=False)
            state = dict(releaseId=args.release, jarSha256=args.jar_sha,
                         previousJar=str(LIVE.resolve()), previousJarSha256=digest(LIVE),
                         stage='PREPARED')
            previous = ROOT / 'current.json'
            prior = json.loads(trusted(previous).read_bytes()) if previous.exists() else None
            state['previousRelease'] = {k: prior[k] for k in ('releaseId', 'jarSha256')} if prior else None
            record(state_path, state)
            atomic(HOLD, (args.release + '\n').encode(), mode=0o644)
            stage(state_path, state, 'HOLD')
            wait_drained(effective)
            stage(state_path, state, 'DRAINED')
            if args.phase == 'hold': return
        require(state is not None and HOLD.read_text().strip() == args.release, 'HOLD_OWNER_MISMATCH')
        if args.phase in ('deploy', 'activate'):
            require(state['stage'] == 'DRAINED', 'ACTIVATION_STATE_INVALID')
            require(LIVE.resolve().as_posix() == state['previousJar'] and digest(LIVE) == state['previousJarSha256'],
                    'LIVE_RELEASE_CHANGED')
            wait_drained(effective)
            if not target.exists():
                temporary = target.with_suffix('.jar.pending')
                require(not temporary.exists(), 'JAR_COPY_PENDING')
                shutil.copyfile(incoming, temporary); temporary.chmod(0o644)
                check_jar(temporary, args.jar_sha); os.replace(temporary, target)
            stage(state_path, state, 'ACTIVATING')
            switch_jar(target)
            command(['systemctl', 'restart', SERVICE], timeout=60)
            stage(state_path, state, 'ACTIVATED')
            if args.phase == 'activate': return
        if args.phase in ('deploy', 'resume'):
            require(state['stage'] in ('ACTIVATED', 'VERIFIED') and LIVE.resolve() == target and digest(LIVE) == args.jar_sha,
                    'RESUME_STATE_INVALID')
            wait_ready()
            # Fresh checks after readiness, before reopening. No inference or POST probe.
            require(not any(probes.drain(environment()).values()), 'DRAIN_CHANGED')
            stage(state_path, state, 'VERIFIED')
            HOLD.unlink()
            stage(state_path, state, 'COMPLETE')
        elif args.phase == 'rollback':
            require(state['stage'] in ('ACTIVATING', 'ACTIVATED', 'VERIFIED'), 'ROLLBACK_STATE_INVALID')
            prior = state.get('previousRelease')
            require(prior is not None and prior['jarSha256'] == state['previousJarSha256'],
                    'PREVIOUS_RELEASE_REQUIRED')
            wait_drained(effective)
            previous_jar = trusted_release_jar(Path(state['previousJar']))
            require(previous_jar == RELEASES / (prior['releaseId'] + '.jar'), 'PREVIOUS_RELEASE_PATH_INVALID')
            check_jar(previous_jar, prior['jarSha256'])
            stage(state_path, state, 'ROLLING_BACK')
            switch_jar(previous_jar)
            command(['systemctl', 'restart', SERVICE], timeout=60)
            wait_ready()
            HOLD.unlink()
            stage(state_path, state, 'ROLLED_BACK')
            record(ROOT / 'current.json', prior)
        elif args.phase == 'abort':
            # Before activation only: no partial cross-system rollback is guessed.
            require(state['stage'] in ('HOLD', 'DRAINED') and digest(LIVE) == state['previousJarSha256'],
                    'ABORT_REQUIRES_UNCHANGED_RELEASE')
            previous = ROOT / 'current.json'
            require(previous.is_file(), 'PREVIOUS_RELEASE_REQUIRED')
            prior = json.loads(trusted(previous).read_bytes())
            probes.backend_health()
            HOLD.unlink(); stage(state_path, state, 'ABORTED')
        else:
            require(args.phase == 'activate', 'PHASE_INVALID')
        if state['stage'] == 'COMPLETE': record(ROOT / 'current.json', state)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('check', 'deploy', 'hold', 'activate', 'resume', 'abort', 'rollback'))
    parser.add_argument('release'); parser.add_argument('jar_sha')
    parser.add_argument('--expected-current-release')
    parser.add_argument('--expected-current-jar-sha')
    try: run(parser.parse_args())
    except Exception as error:
        detail = str(error)
        code = detail if re.fullmatch('[A-Z_]+', detail) else type(error).__name__
        payload = {'stage': 'STOPPED', 'reason': code, 'maintenanceMayRemain': True}
        if detail and detail != code:
            payload['detail'] = detail[:240]
        print(json.dumps(payload), flush=True)
        raise SystemExit(1)
