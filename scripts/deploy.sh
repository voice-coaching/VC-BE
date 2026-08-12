#!/usr/bin/env bash
set -Eeuo pipefail

readonly RELEASE_ID="${1:?release id is required}"
if [[ ! "${RELEASE_ID}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Release id must be a 40-character Git commit SHA." >&2
  exit 2
fi
readonly APP_ROOT="/opt/alpha"
readonly RELEASES_DIR="${APP_ROOT}/releases"
readonly APP_LINK="${APP_ROOT}/app.jar"
readonly INCOMING_JAR="/tmp/alpha-${RELEASE_ID}.jar"
readonly RELEASE_JAR="${RELEASES_DIR}/${RELEASE_ID}.jar"
readonly SERVICE_NAME="alpha-backend"
readonly HEALTH_URL="http://127.0.0.1:8080/v3/api-docs"

previous_release=""
if [[ -L "${APP_LINK}" ]]; then
  previous_release="$(readlink -f "${APP_LINK}" || true)"
fi

rollback() {
  if [[ -n "${previous_release}" && -f "${previous_release}" ]]; then
    echo "Deployment failed. Rolling back to ${previous_release}."
    ln -sfn "${previous_release}" "${APP_LINK}"
    systemctl restart "${SERVICE_NAME}"
  else
    echo "Deployment failed and no previous release is available."
  fi
}

trap rollback ERR

test -s "${INCOMING_JAR}"
install -d -m 755 "${RELEASES_DIR}"
install -m 644 "${INCOMING_JAR}" "${RELEASE_JAR}"
ln -sfn "${RELEASE_JAR}" "${APP_LINK}"
systemctl restart "${SERVICE_NAME}"

for attempt in {1..30}; do
  if systemctl is-active --quiet "${SERVICE_NAME}" && curl --fail --silent "${HEALTH_URL}" > /dev/null; then
    rm -f "${INCOMING_JAR}"
    trap - ERR
    echo "Release ${RELEASE_ID} deployed successfully."
    exit 0
  fi
  sleep 2
done

echo "Health check failed for release ${RELEASE_ID}." >&2
exit 1
