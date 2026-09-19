#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
tls_dir=$(mktemp -d)
container_name="vc-be-redis-integration-$$"
redis_image="redis:8.0-alpine@sha256:5f61955be8ab2ccee9372b84ae4d4da2e2b156f87281e3f218544055e7ee04d4"
jdk_home=${JAVA_HOME:-${VC_BE_JAVA_21_HOME:-}}

if [[ -z "$jdk_home" || ! -x "$jdk_home/bin/java" || ! -x "$jdk_home/bin/keytool" ]]; then
  echo "JAVA_HOME must point to an installed Java 21 JDK" >&2
  exit 2
fi
if ! "$jdk_home/bin/java" -version 2>&1 | head -n 1 | grep -Eq 'version "21([.\"]|$)'; then
  echo "JAVA_HOME must point to an installed Java 21 JDK" >&2
  exit 2
fi

cleanup() {
  docker rm -f "$container_name" >/dev/null 2>&1 || true
  find "$tls_dir" -type f -delete
  rmdir "$tls_dir" 2>/dev/null || true
}
trap cleanup EXIT

openssl req -x509 -nodes -newkey rsa:2048 \
  -keyout "$tls_dir/server.key" \
  -out "$tls_dir/server.crt" \
  -days 1 \
  -subj '/CN=localhost' \
  -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
  >/dev/null 2>&1
chmod 0755 "$tls_dir"
chmod 0644 "$tls_dir/server.key" "$tls_dir/server.crt"
"$jdk_home/bin/keytool" -importcert -noprompt \
  -alias synthetic-redis \
  -file "$tls_dir/server.crt" \
  -keystore "$tls_dir/truststore.p12" \
  -storetype PKCS12 \
  -storepass synthetic-truststore-password \
  >/dev/null 2>&1
chmod 0644 "$tls_dir/truststore.p12"

docker run -d \
  --name "$container_name" \
  -p 127.0.0.1::6379 \
  -v "$tls_dir:/certs:ro" \
  "$redis_image" \
  redis-server \
  --port 0 \
  --tls-port 6379 \
  --tls-cert-file /certs/server.crt \
  --tls-key-file /certs/server.key \
  --tls-ca-cert-file /certs/server.crt \
  --tls-auth-clients no \
  --requirepass synthetic-integration-password \
  >/dev/null

redis_port=$(docker port "$container_name" 6379/tcp | sed 's/.*://')
for _attempt in $(seq 1 30); do
  if (exec 3<>/dev/tcp/127.0.0.1/"$redis_port") 2>/dev/null; then
    exec 3>&-
    break
  fi
  sleep 0.1
done

cd "$repo_root"
JAVA_HOME="$jdk_home" \
PATH="$jdk_home/bin:$PATH" \
JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$tls_dir/truststore.p12 -Djavax.net.ssl.trustStorePassword=synthetic-truststore-password" \
VC_BE_TEST_REDIS_HOST=127.0.0.1 \
VC_BE_TEST_REDIS_PORT="$redis_port" \
VC_BE_TEST_REDIS_PASSWORD=synthetic-integration-password \
  bash ./gradlew test --tests '*BackendAnalysisRedisIntegrationTest'
