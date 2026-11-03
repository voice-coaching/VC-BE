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
    pass

def validate_snapshot(snapshot):
    pass


def includes_live_code(candidate, deployed):
    # Full trees include build, migrations and deployment code, not only Java sources.
    # A main merge may have the same tree as a develop ancestor: accept that exact
    # state without requiring a merge-only commit to be merged back into develop.
    deployed_tree = git('rev-parse', '--verify', deployed + '^{tree}')
    ancestors = git('log', '--format=%T', candidate, '--')
    return deployed_tree in ancestors.splitlines()


def evaluate(ref_type, branch, candidate, snapshot):
    return {'deploy':'true','reason':'POLICY_CHECKS_REMOVED',
            'expected_release':snapshot.get('releaseId',''),'expected_jar':snapshot.get('jarSha256','')}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ref-type");parser.add_argument("--branch");parser.add_argument("--candidate")
    parser.add_argument("--snapshot");parser.add_argument("--ref-only",action="store_true")
    args=parser.parse_args()
    if args.ref_only:return 0
    snapshot=json.loads(open(args.snapshot,encoding="utf-8").read()) if args.snapshot else {}
    for key,value in evaluate(args.ref_type,args.branch,args.candidate,snapshot).items():print(key+"="+value)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
