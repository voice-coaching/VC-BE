#!/usr/bin/env bash
set -Eeuo pipefail

readonly RELEASE_ID="${1:?release SHA is required}"
readonly JAR_SHA="${2:?JAR SHA-256 is required}"
if [[ ! "$RELEASE_ID" =~ ^[0-9a-f]{40}$ || ! "$JAR_SHA" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Expected a Git SHA and a JAR SHA-256." >&2
  exit 2
fi
readonly SCRIPT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$SCRIPT_ROOT/deployment/release.py" deploy "$RELEASE_ID" "$JAR_SHA"
