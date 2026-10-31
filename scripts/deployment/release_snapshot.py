"""Read only the committed live release identity, under the deployment lock."""
import fcntl
import json
import os
from pathlib import Path
import re
import stat
from common import ROOT, HOLD, digest, require, trusted

LIVE = Path('/opt/alpha/app.jar')
RELEASES = Path('/opt/alpha/releases')
SERVICE_DIRECTORY = Path('/opt/alpha')


def trusted_release_jar(path):
    # Existing EC2 layout gives the service directory to the deployment user.
    # The versioned releases directory/JAR and every other ancestor stay root
    # owned, without links or group/world write. Root-owned current.json also
    # pins the exact file digest below; do not chmod/chown the live service.
    for part in (path, *path.parents):
        info = part.lstat()
        require(not stat.S_ISLNK(info.st_mode) and not info.st_mode & 0o022
                and (info.st_uid == 0 or part == SERVICE_DIRECTORY), 'UNTRUSTED_RELEASE_PATH')
    return path


def current_snapshot():
    # The caller must hold deployment.lock (shared for a read, exclusive to deploy).
    require(not HOLD.exists(), 'DEPLOYMENT_ALREADY_PENDING_INSPECT')
    state = json.loads(trusted(ROOT / 'current.json').read_bytes())
    release, sha = state.get('releaseId'), state.get('jarSha256')
    require(isinstance(release, str) and re.fullmatch('[0-9a-f]{40}', release), 'CURRENT_RELEASE_INVALID')
    require(isinstance(sha, str) and re.fullmatch('[0-9a-f]{64}', sha), 'CURRENT_RELEASE_INVALID')
    require(state.get('stage', 'COMPLETE') == 'COMPLETE', 'CURRENT_RELEASE_NOT_COMPLETE')
    target = trusted_release_jar(LIVE.resolve())
    require(target == RELEASES / (release + '.jar') and digest(target) == sha, 'CURRENT_RELEASE_DRIFT')
    return {'releaseId': release, 'jarSha256': sha}


def main():
    try:
        require(os.geteuid() == 0, 'ROOT_REQUIRED')
        trusted(ROOT)
        # Never create state or silently wait behind a partially active deployment.
        with trusted(ROOT / 'deployment.lock').open('r') as lock:
            fcntl.flock(lock.fileno(), fcntl.LOCK_SH | fcntl.LOCK_NB)
            print(json.dumps(current_snapshot(), sort_keys=True))
        return 0
    except Exception as error:
        code = str(error)
        print(json.dumps({'error': code if re.fullmatch('[A-Z_]+', code) else 'LIVE_SNAPSHOT_UNAVAILABLE'}))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
