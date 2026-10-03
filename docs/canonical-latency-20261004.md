# Canonical latency changes — 2026-10-04

Local implementation, not an operational deployment or inference acceptance record.

## Dispatch and database

V38 adds `analysis_request_outbox.busy_count` (default 0) and `analysis_dispatch_gates(endpoint_sha256 PK, claim_id, claim_until, next_attempt_at)`. Existing `attempt_count` continues to count transport/contract failures; confirmed RunPod 429 `CAPACITY_EXCEEDED` increments only busy count. Other 429s retain the failure budget. Retry-After is parsed as seconds or an HTTP date; the bounded local fallback grows from 1 to 5 seconds with up to 250 ms jitter. Request deadlines remain fixed.

The gate serializes dispatch per endpoint across Backend instances with a 60-second lease. One oldest PENDING row is considered per polling tick; no database transaction spans HTTP. Canceled/stale/terminal requests are skipped. A successful worker claim prevents an uncertain submit response from failing live inference. Expired unsent requests use `analysis_execution_timeout`.

## Verification and public status

`CanonicalArtifactLoader` uses a shared two-thread bounded read pool. Callback verification can reuse journal bytes only when an independently VERIFIED receipt has exactly the same manifest and the staged artifact's version reference, size and SHA match. A worker's self-reported upload state is insufficient. Semantic verification still runs with the installed frozen verifier. This is not a change to v4's required B2 receipt.

`GET /api/training-sessions/{sessionId}/analysis/status` adds nullable `deadlineAt` and `serverTime` timestamps. The reader supplies the original execution deadline and current UTC time. Clients can derive remaining time without trusting their device clock. Timeout on a client does not mutate server status.

Structured spans separate evidence reads, semantic subprocess work, receipt commit, callback reads/verification, and result commit. No raw evidence, transcript, credentials or SDK exception text is logged.

## Resident identity prerequisite

The shared v4 schema, `CanonicalSemanticVerifier`, and H5 summary adapter recognize the additive `canonical_resident_core_20261004_v1` / `canonical_resident_h5_20261004_v1` identity. AI owns the builder and exporter. Install the new offline verifier bundle (including old frozen histories) before deploying this JAR. The new core pin is `ce16f635da012237b5316b36161d5eeff9432279cd08dbbe7967f5ab7ace7f7e`, H5 pin `885a2f0a67494dc3bebed28962222bfe95a0143292fe2de04194b84ea8beadc5`, lock `1945dcf2fad7fc97621830abed34353c2d372d8224cc9ad11a9c422eabfdf9f4`.

No v5 handoff, post-result archive worker, writer credential injection, or heartbeat ownership transfer is implemented here. Existing result completion still waits for verified v4 retention. No admission flag is enabled by this migration.

Validation: offline `compileJava` and static source/contract checks. No migration against an actual database, automated tests, deployment, or inference QA was performed. Target branch: `develop`.
