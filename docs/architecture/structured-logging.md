# Structured backend logs and Grafana request tracing

Related issue: #162. The backend emits one JSON object per log line to stdout and,
when `LOGGING_FILE_NAME` is configured, to the existing rolling log file. The
existing production path is `application.log -> Alloy -> Loki -> Grafana`.

## Request and analysis fields

| Field | Meaning |
| --- | --- |
| `@timestamp`, `level`, `service_name` | Timestamp, severity and service; default service `alpha-backend` |
| `event` | Searchable event name |
| `request_id` | Server-generated UUID for one HTTP request; returned as `X-Request-ID` |
| `http_method`, `route`, `http_status`, `duration_ms` | HTTP method, registered route template, final status and elapsed milliseconds |
| `error_code` | Bounded machine-readable code; `HTTP_<status>` fallback for uncoded HTTP errors |
| `exception_type`, `stack_trace` | Exception type and bounded stack locations, without exception messages |
| `analysis_id` | Existing analysis result ID |
| `analysis_request_id` | Existing signed analysis request event ID; distinct from the HTTP request ID |
| `execution_id` | Existing canonical execution ID |
| `queue_age_ms` | Dispatch queue age |

Every `/api/` request receives a server-generated request ID, including security
rejections. Caller-provided IDs are ignored. The CORS exposed headers include
`X-Request-ID`; frontend changes are optional for displaying or reporting this ID.
No request/response bodies, status contracts, endpoint paths, authentication rules
or signed AI payloads change. No migration or new HTTP endpoint is required.

Completion event `http_request_completed` records status and duration once, also
for async completion. MDC is restored when a request or callback finishes.
Routes use registered templates such as `/api/training-sessions/{sessionId}`;
unmatched paths become `<unmapped>`. Query strings, raw path values, authorization
headers, cookies and bodies are not read into the request log.

API business rejection is `api_rejected`; unexpected exception is `api_failed`.
Expected client rejection and successful requests are INFO; HTTP 5xx are ERROR.
Some existing generic handlers omit an API error code; this change preserves
those response bodies and uses a log-only fallback.

Analysis events include `analysis_queued`, `analysis_dispatch_attempt`,
`analysis_dispatch_finished`, `analysis_capacity_wait`,
`analysis_dispatch_failed`, `analysis_handoff_processing`, `analysis_completed`
and `analysis_failed`. Queue and terminal events run after transaction commit,
so rolled-back operations do not appear completed. Verification/dispatch events
describe attempts and do not imply a committed result. HTTP and background events
can be joined using `analysis_id`, `analysis_request_id` and `execution_id`;
the original HTTP request ID is present on the queued event, not automatically
copied to every subsequent worker thread. Logs are observational, not a billing
ledger or an exactly-once delivery guarantee.

### 2026-11-02 main/develop integration

The integration keeps main's existing identity adapter, dispatch/retry behavior,
disabled timeout sweeper and handoff processing behavior. It adds develop's
structured logging and bounded-H5 schema revision without restoring removed
admission or verification checks. Authentication remains main's unverified
identity selection behavior; the logging integration tests use that existing
adapter rather than asserting develop's former authentication rejection.
The CORS configuration retains main's wildcard headers and explicitly exposes
`X-Request-ID` for credentialed browser requests.

`analysis_handoff_processing` records processing only. Main does not invoke the
semantic verifier at this point, so emitting `analysis_evidence_verified` would
incorrectly claim verification. Disabled capacity gates and timeout sweeps do not
emit capacity-wait or timeout events. Deployment workflows and release snapshot
handling remain main's versions; snapshot PR #167 is outside this integration.

Integration validation compiles all production/test sources and packages the
executable JAR. Logging tests, V6 schema pins and audio round-trip tests are run
separately. Two existing `UnifiedV6AudioContractTest` rejection tests fail both
on unmodified main `abf9930` and the integration: `missingMediaDiscriminatorsAndWrongTupleAreRejected`
and `audioDoesNotRequireOptionalVideoReadinessAndExplicitRetiredSchemaIsRejected`.
They expect checks removed by main. Their assertions are retained; this is not
a claim that the full test suite passes. Main's existing compile-only CI and
deployment workflow are unchanged by this merge.

## Privacy and retention

The structured encoder permits only the documented operational field names.
Arbitrary MDC and SLF4J key/value fields are excluded. The stack printer emits at
most three exception types and twenty locations per cause, without throwable or
suppressed messages. The exception handler uses static messages and error codes.
This protects these logging paths; it does not sanitize arbitrary text written
by every third-party library or rewrite logs already stored before deployment.
New logging calls must continue to use static messages and safe machine codes.

Existing file rotation remains 10 MB per file, seven history periods and a 200 MB
archive cap. This cap excludes the active file and is not a total host disk quota.
Existing Loki retention and query lookback remain 72 hours. No historical log or
release cleanup, extra storage, new container or new datasource is part of this
change.

## Collector and dashboard

`scripts/observability/backend-process.alloy` replaces only the existing backend
processor. File sources, persistent read positions, nginx pipelines and Loki
endpoint are preserved. New JSON lines and old timestamped plaintext lines are
both accepted. INFO/WARN/ERROR were already collected before this change.

Existing indexed labels remain `service_name="alpha-backend"`,
`environment="prod"`, `source="application"` and `level` (uppercase). Request,
analysis and execution IDs are structured metadata, never indexed labels.
Do not use these unbounded IDs as stream labels. Numeric filters use LogQL, e.g.:

```logql
{service_name="alpha-backend",environment="prod"}
  | event="http_request_completed" | duration_ms >= 1000
```

```logql
{service_name="alpha-backend",environment="prod"}
  | request_id="<server-returned-UUID>"
```

The dedicated `Voice Coaching` dashboard at `/d/vc-be-request-logs` contains
request count, 5xx count, requests of at least one second, request logs,
API/analysis errors, quota/expiry rejections, analysis lifecycle and all backend
logs. Route and request ID filters accept regular expressions (`.*` means all).
Count queries discard metadata IDs before range aggregation. An empty panel may
mean no matching events in the selected period. New structured request panels
need the backend change deployed; pre-deployment plaintext remains in all logs.
The dashboard links to native Logs Drilldown, using the existing Loki datasource.

## Safe rollout and verification

`configuration.py --datasource-uid <existing-uid>` renders importable dashboard
JSON. The operator discovers the existing datasource UID from Grafana SQLite
using a read-only connection and provisions only the dedicated dashboard. The
operator does not change datasource credentials, access permissions or existing
dashboards. It requires root on the existing Linux host.
Registration and preservation checks cover both legacy dashboard rows and
Grafana 13 unified resources, including converted V2 panel elements.

Prepare a ZIP with `configuration.py`, `backend-process.alloy`, and
`request.json` containing `expected_release` and preferably
`expected_config_sha256`. By default:

```sh
python3 scripts/observability/operator.py --bundle reviewed-bundle.zip
```

This validates the release/config guards, full Alloy configuration and all eight
dashboard queries against real Loki without changing configuration. To apply an
explicitly authorized and reviewed bundle, add `--apply`. The stdin transport
also accepts an injected bundle whose `request.json` includes `apply: true`.

The operator also requires the Loki WAL filesystem usage gauge below 90%, even
if `/ready` is green. Above that threshold Loki can reject ingestion with
`Ingester is shutting down` while still serving historical queries. Free space
through a separately approved cleanup or storage change before retrying; do not
disable the WAL protection. See [Loki WAL disk pressure](https://grafana.com/docs/loki/latest/operations/storage/wal/).

Application deployment and this operation share the existing deployment lock.
The operator backs up the original config under a private
`/var/backups/alpha-observability/issue162-*` directory. The individual Alloy
Docker bind mount requires an **in-place** config write to preserve its inode;
Alloy reloads through its loopback-only HTTP endpoint, reached in the existing
container network namespace. The operator waits for the successful reload and
ready responses before appending probes; signal delivery alone would race with
the file tailer. The container and read positions are preserved. The host must
already provide `nsenter` and `curl`; no new port is exposed.
Two synthetic log lines validate JSON metadata queries and legacy WARN parsing.
These are observability probes, not user requests or business records.
After an ingestion outage the acceptance check allows up to six minutes for an
existing Alloy batch retry backoff; it preserves queued data and read positions.

Dedicated dashboard/provider files are published atomically, and Grafana alone
is restarted to load the new provider. The operator verifies existing dashboard
and datasource fingerprints, unchanged backend process and backend health.
On failure it restores the collector and removes only its newly created files.
If Grafana already imported the new dashboard, `disableDeletion: true` can leave
that dedicated database object in place after file rollback; review that object
separately, without deleting existing dashboards. No backend restart or app
deployment is performed by this operator. Backend rollout follows the usual
develop PR, team merge and deployment workflow.

Unit tests cover actual Boot encoding/privacy, request/async completion,
authentication and API responses, transaction commit/rollback, collector
replacement, inode-preserving writes, symlink rejection and read-only SQLite.
Native Linux validation is required for the symlink test skipped on Windows.

References: [Spring Boot structured logging](https://docs.spring.io/spring-boot/reference/features/logging.html),
[Alloy processing](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.process/),
[Alloy reload](https://grafana.com/docs/alloy/latest/reference/cli/run/),
[Loki label guidance](https://grafana.com/docs/loki/latest/get-started/labels/bp-labels/),
[Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/).
