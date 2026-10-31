#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 && $# -ne 4 ]]; then
  echo "Expected release SHA, JAR SHA-256, and optionally both current release fingerprints." >&2
  exit 2
fi
readonly RELEASE_ID="${1:?release SHA is required}"
readonly JAR_SHA="${2:?JAR SHA-256 is required}"
if [[ ! "$RELEASE_ID" =~ ^[0-9a-f]{40}$ || ! "$JAR_SHA" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Expected a Git SHA and a JAR SHA-256." >&2
  exit 2
fi
readonly SCRIPT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
expected_args=()
if [[ $# -eq 4 ]]; then
  if [[ ! "$3" =~ ^[0-9a-f]{40}$ || ! "$4" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Expected valid current release fingerprints." >&2
    exit 2
  fi
  expected_args=(--expected-current-release "$3" --expected-current-jar-sha "$4")
fi
exec python3 -B "$SCRIPT_ROOT/deployment/release.py" deploy "$RELEASE_ID" "$JAR_SHA" "${expected_args[@]}"
