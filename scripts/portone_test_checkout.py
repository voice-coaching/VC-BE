"""Loopback-only payment sandbox. Starts a NEW local DB and backend; never uses production DB/JWT.

Run after bootJar: python scripts/portone_test_checkout.py --env-file tmp/portone-test.local.env
No payment is submitted until the user opens and completes the official PG checkout.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib import error, request
from urllib.parse import urlsplit
import uuid
import webbrowser


ROOT = Path(__file__).resolve().parents[1]
NO_WINDOW = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
OPENER = request.build_opener(request.ProxyHandler({}))


def load_credentials(path: Path) -> dict[str, str]:
    allowed = {"PORTONE_STORE_ID", "PORTONE_TEST_CHANNEL_KEY", "PORTONE_API_SECRET", "PORTONE_WEBHOOK_SECRET"}
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if separator and key.strip() in allowed:
            values[key.strip()] = value.strip().strip("\"'")
    if any(not values.get(key) for key in allowed - {"PORTONE_WEBHOOK_SECRET"}):
        raise RuntimeError("Required PortOne test settings are missing; values were not printed")
    return values


def api(base: str, path: str, method="GET", data=None, token=None, headers=None):
    merged = {"Content-Type": "application/json", **(headers or {})}
    if token:
        merged["Authorization"] = "Bearer " + token
    req = request.Request(base + path, data=None if data is None else json.dumps(data).encode(), headers=merged, method=method)
    try:
        with OPENER.open(req, timeout=20) as response:
            return response.status, json.load(response)
    except error.HTTPError as failure:
        try:
            return failure.code, json.load(failure)
        except (ValueError, OSError):
            return failure.code, {"result": False, "code": "LOCAL_BACKEND_ERROR"}


def isolated_environment() -> dict[str, str]:
    # Do not inherit production feature flags, DB credentials, cloud credentials or Spring overrides.
    return {key: value for key, value in os.environ.items() if key.upper() in {
        "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "PATH", "USERPROFILE", "APPDATA", "LOCALAPPDATA", "HOME",
    }}


class Sandbox:
    def __init__(self, args):
        self.args = args
        self.run = ROOT / "tmp" / ("portone-checkout-" + uuid.uuid4().hex)
        self.run.mkdir(parents=True)
        self.backend = None
        self.db_started = False
        self.logs = []
        self.base = f"http://127.0.0.1:{args.backend_port}"
        self.session = secrets.token_urlsafe(32)
        self.email = f"payment-test-{uuid.uuid4().hex}@example.invalid"
        self.password = "Test1!" + secrets.token_urlsafe(32)

    def command(self, *args):
        with (self.run / "database.log").open("ab") as log:
            subprocess.run([str(arg) for arg in args], check=True, stdout=log, stderr=log,
                           env=isolated_environment(), creationflags=NO_WINDOW, timeout=60)

    def start(self):
        credentials = load_credentials(Path(self.args.env_file))
        for port in (self.args.port, self.args.backend_port, self.args.pg_port):
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", port))  # Refuse to reuse any existing service.
        binary = lambda name: Path(self.args.pg_bin) / (name + (".exe" if os.name == "nt" else ""))
        self.binary = binary
        self.command(binary("initdb"), "-D", self.run / "data", "-U", "postgres", "--auth=trust", "--encoding=UTF8", "--locale=C")
        self.command(binary("pg_ctl"), "-D", self.run / "data", "-l", self.run / "postgres.log", "-o",
                     f"-h 127.0.0.1 -p {self.args.pg_port}", "-w", "start")
        self.db_started = True
        self.command(binary("createdb"), "-h", "127.0.0.1", "-p", self.args.pg_port, "-U", "postgres", "payment_sandbox")
        env = isolated_environment()
        env.update(credentials)
        env.update({
            "DB_URL": f"jdbc:postgresql://127.0.0.1:{self.args.pg_port}/payment_sandbox",
            "DB_USERNAME": "postgres", "DB_PASSWORD": "", "JWT_SECRET": secrets.token_urlsafe(48),
            "JWT_ISSUER": "payment-local-sandbox", "SPRING_PROFILES_ACTIVE": "payment-test",
            "PAYMENT_TEST_ENABLED": "true", "PAYMENT_TEST_ALLOWED_USER_IDS": "1",
            "SERVER_ADDRESS": "127.0.0.1", "SERVER_PORT": str(self.args.backend_port),
            "MANAGEMENT_SERVER_PORT": "0", "AUTH_COOKIE_SECURE": "false", "AUTH_COOKIE_SAME_SITE": "Lax",
            "SPRING_JPA_SHOW_SQL": "false", "LOGGING_LEVEL_ROOT": "WARN",
            "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB": "WARN", "LOGGING_LEVEL_ORG_HIBERNATE_SQL": "WARN",
            "LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND": "OFF",
            "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BOOT_SECURITY_AUTOCONFIGURE": "ERROR",
        })
        jars = list((ROOT / "build/libs").glob("*.jar"))
        jars = [jar for jar in jars if not jar.name.endswith("-plain.jar")]
        if len(jars) != 1:
            raise RuntimeError("Build bootJar first; expected exactly one executable JAR")
        java = Path(self.args.java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        log = (self.run / "backend.log").open("ab")
        self.logs.append(log)
        self.backend = subprocess.Popen([str(java), "-jar", str(jars[0])], cwd=ROOT, env=env,
                                        stdout=log, stderr=log, creationflags=NO_WINDOW)
        for _ in range(90):
            if self.backend.poll() is not None:
                raise RuntimeError("Sandbox backend failed; inspect the local backend.log")
            try:
                status, _ = api(self.base, "/v3/api-docs")
                if status == 200:
                    break
            except (OSError, ValueError):
                pass
            time.sleep(1)
        else:
            raise RuntimeError("Sandbox backend startup timed out")
        status, signup = api(self.base, "/api/auth/signup", "POST", {
            "email": self.email, "password": self.password, "nickname": "결제테스트",
            "termsAgreed": True, "privacyAgreed": True,
        })
        if status != 200 or signup.get("data", {}).get("userId") != 1:
            raise RuntimeError("Isolated test account initialization failed")
        self.token = signup["data"]["accessToken"]

    def call(self, path, method="GET", data=None, headers=None):
        status, result = api(self.base, path, method, data, self.token, headers)
        if status == 401:
            login_status, login = api(self.base, "/api/auth/login", "POST", {"email": self.email, "password": self.password})
            if login_status != 200:
                return status, {"result": False, "code": "LOCAL_SESSION_EXPIRED"}
            self.token = login["data"]["accessToken"]
            return api(self.base, path, method, data, self.token, headers)
        return status, result

    def smoke(self):
        status, profile = self.call("/api/users/me")
        assert status == 200 and profile["data"]["membershipType"] == "free"
        status, order = self.call("/api/payment-orders", "POST", headers={"Idempotency-Key": str(uuid.uuid4())})
        assert status == 200 and order["data"]["environment"] == "TEST" and order["data"]["totalAmount"] == 15000
        status, result = self.call("/api/payment-orders/" + order["data"]["orderId"] + "/verify", "POST")
        assert status == 409 and result.get("code") == "PAYMENT_NOT_COMPLETED"
        print("Smoke passed: isolated account=free, order=created, unpaid provider lookup=rejected. No charge submitted.")

    def close(self):
        if self.backend and self.backend.poll() is None:
            self.backend.terminate()
            try:
                self.backend.wait(timeout=20)
            except subprocess.TimeoutExpired:
                self.backend.kill()
                self.backend.wait(timeout=10)
        if self.db_started:
            self.command(self.binary("pg_ctl"), "-D", self.run / "data", "-m", "fast", "-w", "stop")
        for log in self.logs:
            log.close()


def handler(sandbox):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass  # URLs, browser requests and PG redirects are not logged.

        def reply(self, code, body, content_type="application/json; charset=utf-8"):
            raw = body.encode() if isinstance(body, str) else json.dumps(body).encode()
            self.send_response(code)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Referrer-Policy", "no-referrer")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(raw)

        def valid_host(self):
            return self.headers.get("Host") == f"127.0.0.1:{sandbox.args.port}"

        def do_GET(self):
            if not self.valid_host():
                return self.reply(403, {})
            if urlsplit(self.path).path not in ("/", "/return"):
                return self.reply(404, {})
            page = (ROOT / "scripts/portone-test-checkout.html").read_text(encoding="utf-8")
            self.reply(200, page.replace("__LOCAL_SESSION__", sandbox.session), "text/html; charset=utf-8")

        def do_POST(self):
            if not self.valid_host() or self.headers.get("Origin") != f"http://127.0.0.1:{sandbox.args.port}" or not secrets.compare_digest(
                    self.headers.get("X-Local-Test-Session", ""), sandbox.session):
                return self.reply(403, {"code": "LOCAL_ORIGIN_REQUIRED"})
            path = urlsplit(self.path).path
            if path == "/local/profile":
                status, result = sandbox.call("/api/users/me")
                if status == 200:
                    result["data"] = {key: result["data"].get(key) for key in ("membershipType", "membershipExpiresAt", "membershipEnvironment")}
            elif path == "/local/orders":
                key = self.headers.get("Idempotency-Key", "")
                status, result = sandbox.call("/api/payment-orders", "POST", headers={"Idempotency-Key": key})
            elif re.fullmatch(r"/local/verify/test_[a-f0-9-]{36}", path):
                order_id = path.rsplit("/", 1)[1]
                status, result = sandbox.call(f"/api/payment-orders/{order_id}/verify", "POST")
            else:
                return self.reply(404, {})
            self.reply(status, result)
    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default=str(ROOT / "tmp/portone-test.local.env"))
    parser.add_argument("--java-home", default=str(ROOT / "tmp/jdk21/jdk-21.0.12+8"))
    parser.add_argument("--pg-bin", default=str(ROOT / "tmp/postgresql17-runtime/pgsql/bin"))
    parser.add_argument("--port", type=int, default=18081)
    parser.add_argument("--backend-port", type=int, default=18080)
    parser.add_argument("--pg-port", type=int, default=15433)
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()
    sandbox = Sandbox(args)
    try:
        sandbox.start()
        if args.smoke:
            sandbox.smoke()
            return
        with ThreadingHTTPServer(("127.0.0.1", args.port), handler(sandbox)) as server:
            print(f"Test checkout ready: http://127.0.0.1:{args.port}/ (Ctrl+C stops this isolated sandbox)")
            print("Use the NHN KCP V2 TEST channel. Complete checkout yourself; paid status requires server verification.")
            if not args.no_browser:
                webbrowser.open(f"http://127.0.0.1:{args.port}/")
            server.serve_forever()
    except KeyboardInterrupt:
        pass
    except Exception:
        # Exceptions from urllib/subprocess can embed credentials or response content; do not echo them.
        print(f"Sandbox failed. Local diagnostic directory: {sandbox.run}")
        raise SystemExit(1)
    finally:
        sandbox.close()


if __name__ == "__main__":
    main()
