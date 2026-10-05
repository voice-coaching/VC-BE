"""Root-only release state machine. Errors retain maintenance; no blind rollback.

The root-owned runtime profile identifies a reviewed compatible AI/verifier/FE set.
Ordinary CI can deploy only while that exact RunPod set is already installed.
For a coordinated schema transition use hold, activate, external RunPod activation,
then resume. All phases use the same lock and immutable plan digest.
"""
import argparse
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import time
import zipfile
from common import ROOT, PROFILE, HOLD, SERVICE, atomic, command, digest, environment, record, require, trusted
import probes

LIVE = Path('/opt/alpha/app.jar')
ENV = Path('/etc/alpha-canonical-secrets/backend-runtime.env')

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

def wait_ready(doc):
    deadline, consecutive, last = time.monotonic() + 300, 0, 'NOT_READY'
    while time.monotonic() < deadline:
        try:
            probes.ready(doc, environment())
            consecutive += 1
            if consecutive == 3: return
        except Exception as error:
            consecutive = 0
            last = str(error) if re.fullmatch('[A-Z_]+', str(error)) else 'PROBE_FAILED'
        print(json.dumps({'stage': 'READINESS', 'consecutive': consecutive, 'reason': last}), flush=True)
        time.sleep(15)
    raise RuntimeError('CANONICAL_READINESS_TIMEOUT')

def check_jar(path, sha, doc):
    require(path.is_file() and not path.is_symlink() and digest(path) == sha, 'JAR_DIGEST_MISMATCH')
    with zipfile.ZipFile(path) as jar:
        for name, wanted in doc['schemaDigests'].items():
            require(hashlib.sha256(jar.read('BOOT-INF/classes/contracts/' + name)).hexdigest() == wanted,
                    'JAR_SCHEMA_MISMATCH')

def switch_jar(target):
    link = LIVE.with_name('.app.jar.pipeline-pending')
    require(not link.exists() and not link.is_symlink(), 'JAR_SWITCH_PENDING')
    link.symlink_to(target)
    os.replace(link, LIVE)

def set_verifier(root, effective, doc):
    trusted(ENV)
    require(ENV.stat().st_mode & 0o777 == 0o600, 'ENV_PERMISSIONS')
    old = ENV.read_bytes()
    replacements = {
        'ANALYSIS_CANONICAL_EVIDENCE_SEMANTIC_PYTHON': probes.verifier_python(doc, effective),
        'ANALYSIS_CANONICAL_EVIDENCE_SEMANTIC_SCRIPT': str(root / 'app/deploy/runpod/canonical_verify.py'),
        'ANALYSIS_CANONICAL_EVIDENCE_SEMANTIC_CORE_ROOT': str(root / 'frozen/core'),
        'ANALYSIS_CANONICAL_EVIDENCE_SEMANTIC_LLM_ROOT': str(root / 'frozen/llm'),
    }
    lines = old.decode().splitlines()
    for key, value in replacements.items():
        matches = [i for i, line in enumerate(lines) if line.startswith(key + '=')]
        require(len(matches) == 1 and lines[matches[0]].split('=', 1)[1] == effective[key], 'EFFECTIVE_ENV_DRIFT')
        lines[matches[0]] = key + '=' + value
    return old, ('\n'.join(lines) + '\n').encode()

def stage(state_path, state, name):
    state['stage'] = name
    state['updatedAt'] = datetime.now(timezone.utc).isoformat()
    record(state_path, state)
    print(json.dumps({'release': state['releaseId'], 'stage': name}), flush=True)

def run(args):
    require(os.geteuid() == 0 and re.fullmatch('[0-9a-f]{40}', args.release), 'ROOT_RELEASE_REQUIRED')
    require(re.fullmatch('[0-9a-f]{64}', args.jar_sha), 'JAR_SHA_REQUIRED')
    ROOT.mkdir(mode=0o755, exist_ok=True); trusted(ROOT)
    require(ROOT.stat().st_mode & 0o777 == 0o755, 'MAINTENANCE_DIRECTORY_PERMISSIONS')
    with (ROOT / 'deployment.lock').open('a') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        doc = probes.profile(PROFILE)
        profile_sha = digest(PROFILE)
        directory = ROOT / 'releases' / args.release
        state_path = directory / 'state.json'
        effective = environment()
        verifier = None
        if args.phase not in ('abort', 'rollback'):
            verifier = probes.verifier(doc, effective)
            probes.frontend(doc)
        target = Path('/opt/alpha/releases') / (args.release + '.jar')
        incoming = Path('/var/tmp') / ('alpha-' + args.release + '.jar')
        source = target if target.exists() else incoming
        if args.phase not in ('abort', 'rollback'): check_jar(source, args.jar_sha, doc)
        if args.phase == 'check':
            probes.pod_identity(doc, effective)
            print(json.dumps({'stage': 'PREFLIGHT_READY', 'release': args.release,
                              'profileSha256': profile_sha, 'jarSha256': args.jar_sha})); return
        state = json.loads(state_path.read_bytes()) if state_path.exists() else None
        if state:
            require(state['profileSha256'] == profile_sha and state['jarSha256'] == args.jar_sha,
                    'PENDING_RELEASE_CHANGED')
            if state['stage'] == 'COMPLETE':
                require(not HOLD.exists() and LIVE.resolve() == target and digest(LIVE) == args.jar_sha,
                        'COMPLETE_RELEASE_DRIFT')
                probes.ready(doc, effective)
                print(json.dumps({'stage': 'ALREADY_COMPLETE', 'release': args.release})); return
        if args.phase in ('deploy', 'hold'):
            require(state is None and not HOLD.exists(), 'DEPLOYMENT_ALREADY_PENDING_INSPECT')
            # CI may not initiate a cross-contract transition before AI is ready.
            if args.phase == 'deploy': probes.pod_identity(doc, effective)
            gate_configured()
            directory.mkdir(parents=True, mode=0o700, exist_ok=False)
            state = dict(releaseId=args.release, jarSha256=args.jar_sha, profileSha256=profile_sha,
                         previousJar=str(LIVE.resolve()), previousJarSha256=digest(LIVE),
                         profile=doc, stage='PREPARED')
            previous = ROOT / 'current.json'
            prior = json.loads(trusted(previous).read_bytes()) if previous.exists() else None
            state['previousRelease'] = {k: prior[k] for k in ('releaseId', 'jarSha256', 'profile')} if prior else None
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
            old_env, new_env = set_verifier(verifier, effective, doc)
            # Private backups never go into the release manifest/artifact.
            atomic(directory / 'backend-runtime.env.backup', old_env)
            if not target.exists():
                temporary = target.with_suffix('.jar.pending')
                require(not temporary.exists(), 'JAR_COPY_PENDING')
                shutil.copyfile(incoming, temporary); temporary.chmod(0o644)
                check_jar(temporary, args.jar_sha, doc); os.replace(temporary, target)
            stage(state_path, state, 'ACTIVATING')
            atomic(ENV, new_env)
            switch_jar(target)
            command(['systemctl', 'restart', SERVICE], timeout=60)
            stage(state_path, state, 'ACTIVATED')
            if args.phase == 'activate': return
        if args.phase in ('deploy', 'resume'):
            require(state['stage'] in ('ACTIVATED', 'VERIFIED') and LIVE.resolve() == target and digest(LIVE) == args.jar_sha,
                    'RESUME_STATE_INVALID')
            wait_ready(doc)
            # Fresh checks after readiness, before reopening. No inference or POST probe.
            fresh = environment()
            require(fresh['ANALYSIS_CANONICAL_EVIDENCE_SEMANTIC_SCRIPT'] == str(verifier / 'app/deploy/runpod/canonical_verify.py'),
                    'ACTIVE_VERIFIER_CHANGED')
            probes.frontend(doc)
            require(not any(probes.drain(fresh).values()), 'DRAIN_CHANGED')
            stage(state_path, state, 'VERIFIED')
            HOLD.unlink()
            stage(state_path, state, 'COMPLETE')
        elif args.phase == 'rollback':
            require(state['stage'] in ('ACTIVATING', 'ACTIVATED', 'VERIFIED'), 'ROLLBACK_STATE_INVALID')
            prior = state.get('previousRelease')
            require(prior is not None and prior['jarSha256'] == state['previousJarSha256'],
                    'PREVIOUS_COMPATIBLE_MANIFEST_REQUIRED')
            # The operator restores the pinned AI set first, while ingress stays closed.
            # This prevents a blind JAR-only rollback across incompatible contracts.
            probes.verifier(prior['profile'], effective)
            probes.frontend(prior['profile'])
            probes.pod_identity(prior['profile'], effective)
            wait_drained(effective)
            previous_jar = trusted(state['previousJar'])
            check_jar(previous_jar, prior['jarSha256'], prior['profile'])
            backup = trusted(directory / 'backend-runtime.env.backup')
            require(backup.stat().st_mode & 0o777 == 0o600, 'BACKUP_PERMISSIONS')
            stage(state_path, state, 'ROLLING_BACK')
            atomic(ENV, backup.read_bytes())
            switch_jar(previous_jar)
            command(['systemctl', 'restart', SERVICE], timeout=60)
            wait_ready(prior['profile'])
            HOLD.unlink()
            stage(state_path, state, 'ROLLED_BACK')
            record(ROOT / 'current.json', prior)
        elif args.phase == 'abort':
            # Before activation only: no partial cross-system rollback is guessed.
            require(state['stage'] in ('HOLD', 'DRAINED') and digest(LIVE) == state['previousJarSha256'],
                    'ABORT_REQUIRES_UNCHANGED_RELEASE')
            previous = ROOT / 'current.json'
            require(previous.is_file(), 'PREVIOUS_COMPATIBLE_MANIFEST_REQUIRED')
            prior = json.loads(trusted(previous).read_bytes())
            probes.ready(prior['profile'], effective)
            HOLD.unlink(); stage(state_path, state, 'ABORTED')
        else:
            require(args.phase == 'activate', 'PHASE_INVALID')
        if state['stage'] == 'COMPLETE': record(ROOT / 'current.json', state)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('check', 'deploy', 'hold', 'activate', 'resume', 'abort', 'rollback'))
    parser.add_argument('release'); parser.add_argument('jar_sha')
    try: run(parser.parse_args())
    except Exception as error:
        code = str(error) if re.fullmatch('[A-Z_]+', str(error)) else type(error).__name__
        print(json.dumps({'stage': 'STOPPED', 'reason': code, 'maintenanceMayRemain': True}), flush=True)
        raise SystemExit(1)
