"""Fail-closed Git policy for two branches sharing one production service."""
import argparse
import json
import re
import subprocess
import sys

BRANCHES = frozenset(('develop', 'main'))


class PolicyError(RuntimeError):
    pass


def require(condition, code):
    if not condition:
        raise PolicyError(code)


def valid_sha(value, size=40):
    return isinstance(value, str) and re.fullmatch('[0-9a-f]{' + str(size) + '}', value) is not None


def git(*args):
    try:
        result = subprocess.run(['git', *args], capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.TimeoutExpired):
        raise PolicyError('GIT_QUERY_FAILED') from None
    require(result.returncode == 0, 'GIT_QUERY_FAILED')
    return result.stdout.strip()


def validate_ref(ref_type, branch, candidate):
    require(ref_type == 'branch' and branch in BRANCHES, 'UNSUPPORTED_DEPLOYMENT_REF')
    require(valid_sha(candidate), 'INVALID_CANDIDATE_SHA')


def validate_snapshot(snapshot):
    require(isinstance(snapshot, dict) and set(snapshot) == {'releaseId', 'jarSha256'}, 'INVALID_LIVE_SNAPSHOT')
    require(valid_sha(snapshot['releaseId']) and valid_sha(snapshot['jarSha256'], 64), 'INVALID_LIVE_SNAPSHOT')


def includes_live_code(candidate, deployed):
    # Full trees include build, migrations and deployment code, not only Java sources.
    # A main merge may have the same tree as a develop ancestor: accept that exact
    # state without requiring a merge-only commit to be merged back into develop.
    deployed_tree = git('rev-parse', '--verify', deployed + '^{tree}')
    ancestors = git('log', '--format=%T', candidate, '--')
    return deployed_tree in ancestors.splitlines()


def evaluate(ref_type, branch, candidate, snapshot):
    validate_ref(ref_type, branch, candidate)
    validate_snapshot(snapshot)
    require(git('rev-parse', '--is-shallow-repository') == 'false', 'FULL_GIT_HISTORY_REQUIRED')
    require(git('rev-parse', '--verify', candidate + '^{commit}') == candidate, 'INVALID_CANDIDATE_COMMIT')
    # Consult the remote again immediately before activation. A full checkout made
    # before the test/build may no longer contain the current branch tip.
    tip = git('ls-remote', '--exit-code', 'origin', 'refs/heads/' + branch).split()
    require(len(tip) == 2 and valid_sha(tip[0]) and tip[1] == 'refs/heads/' + branch, 'REMOTE_TIP_INVALID')
    if tip[0] != candidate:
        return {'deploy': 'false', 'reason': 'SUPERSEDED_BRANCH_COMMIT'}
    require(includes_live_code(candidate, snapshot['releaseId']), 'LIVE_CODE_NOT_INCLUDED_SYNC_BRANCH_FIRST')
    return {'deploy': 'true', 'reason': 'LIVE_CODE_INCLUDED',
            'expected_release': snapshot['releaseId'], 'expected_jar': snapshot['jarSha256']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ref-type', required=True)
    parser.add_argument('--branch', required=True)
    parser.add_argument('--candidate', required=True)
    parser.add_argument('--snapshot')
    parser.add_argument('--ref-only', action='store_true')
    args = parser.parse_args()
    try:
        validate_ref(args.ref_type, args.branch, args.candidate)
        if args.ref_only:
            return 0
        require(bool(args.snapshot), 'LIVE_SNAPSHOT_REQUIRED')
        with open(args.snapshot, encoding='utf-8') as stream:
            snapshot = json.load(stream)
        result = evaluate(args.ref_type, args.branch, args.candidate, snapshot)
        for key, value in result.items():
            print(key + '=' + value)
        return 0
    except (PolicyError, OSError, ValueError) as error:
        # Never echo Git stderr, URLs, snapshot content or arbitrary parser input.
        print(str(error) if isinstance(error, PolicyError) else 'LIVE_SNAPSHOT_UNREADABLE', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
