# Canonical result adapter boundary (local implementation and design history)

2026-10-04 local change: [latency implementation and resident identity prerequisites](../canonical-latency-20261004.md). Bounded artifact reads and exact verified spool reuse retain independent semantic verification. Result publication still requires the v4 receipt; asynchronous post-result archiving is not implemented.

2026-10-03: [전체 음소 채점 연동](../canonical-scoring-20261003.md). 오프라인 검산된 새 하네스 점수만 기존 overall_score에 저장한다. 수치 판정은 AI 계산기/동결 verifier 책임이며 Controller·Entity·공개 DTO에서 재계산하지 않는다. 과거 null-only 기록은 유지한다.

D1 observed deployment (2026-10-01 15:24 UTC): JAR SHA
`86c2aa795348c716ff0319b9d014089620ae11505db703b55ee2890c63ea9f65`,
V33-V36 applied, service active and legacy readiness ready. Canonical v2 readiness
remains 503 with both support/admission false; internal worker switches default off.
Runtime keys, RunPod orchestration/boot, FE consumers and developer acceptance are
still pending. The following local-only slices are implementation history.

Rollout correction (2026-10-02 KST): this worktree is based on production release
`b410076071c0f0e93e6103872e54082805a5b7da`. Production already applied the example
question seed at V32. The unapplied canonical drafts below were therefore moved
to **V33 registry / V34 receipts / V35 callbacks / V36 journal**. Existing V0-V32
are unchanged. Older numbering below is historical, not a deployment command.
The original dirty worktree is preserved. The new JAR compiles, but admission is
still unconditionally disabled; this is not end-to-end acceptance or activation.

Latest approved storage slice (local, not deployed): [PostgreSQL journal v1](../contracts/runpod_canonical_journal_v1.md)
replaces the proposed RunPod SQLite production dependency. V35 adds immutable
reservation/one-shot producer start, bounded pre-B2 spool and upload checkpoints,
transactional ACK storage and a server-side callback apply queue. New internal
Bearer routes never expose raw spool content to the FE. Existing receipt/inbox
paths require the same reserved identity and manifest; frozen verification stays
independent. Original evidence remains in B2. No automatic deletion/TTL, inference
replay or unknown-outcome PUT retry was added. Flags default off and the explicit
DB spool budget is unset. Compile/static checks are not SQL/HTTP/E2E acceptance.
V32-V35, factory/boot/FE wiring, operational capacity/backup/keys and deployment
remain outstanding. The older delivery/recovery slices below are historical.

Latest delivery slice (local, not deployed): authenticated POST result/ack accepts
the exact stored v4 bytes and only confirms a verified APPLIED inbox/current
immutable result. It never registers/applies a result or renews a lease. Normal
duplicate ingestion shares the stronger current-result check. See the shared
[recovery contract](../contracts/runpod_http_control_v1_2.md#canonical-callback-delivery-and-ack-only-recovery-local-not-deployed).
No migration or public FE API was added by this slice; callback/admission flags remain off.

Latest recovery slice (local, not deployed): the existing upload-URL command can
reopen ANALYZING to UPLOADING for a current verified canonical REJECT/INCONCLUSIVE
result, using `CanonicalRerecordEligibility` shared with public actions. It holds
session then analysis locks, preserves old recording/evidence, and joins upload
intent issuance in one transaction. Existing register/select/analyze routes create
the new attempt. Analyze/retry lock selection before reading their source; select
rechecks the target's ownership/deletion/quality under the session lock. See the
[recovery contract](../api/canonical-analysis-view-v1.md#same-session-re-recording-local-implementation).
FE entry points, runtime acceptance, RunPod execution/delivery, key injection and
deployment remain incomplete; admission remains off. Snapshots below predate this slice.

Latest public-reader slice: `GET /api/v2/analyses/{analysisId}` is implemented
locally with existing JWT/ApiResponse and no-store success/errors. The typed
`CanonicalAnalysisView` allowlist preserves scoped IDs, MFA decimals, ordered
candidates, decision and generation branches without forwarding private callback
or receipt data. Current registry/committed-result checks and fresh READ_COMMITTED
visibility checks distinguish legacy fallback, hidden results and changed attempts.
Backend pre-callback timeout/delivery failure has a fixed public failure mapping.
`CanonicalActionPolicy` shares completion eligibility, retry count and initial
source scope with commands. At this earlier stage same-session recovery was unavailable;
admission is off, so canonical retry is not advertised as available. See the
[public contract](../api/canonical-analysis-view-v1.md). Final compile/resources
passed (24 seconds); nine safe record field sets and six shared schema byte sets
were statically compared. No HTTP/Jackson runtime/SQL/FE QA or deployment occurred.
The earlier snapshots below still describe their own stage.

Latest continuation: explicit `AnalysisExecutionProfile` now propagates through
analyze/retry without modifying public or legacy Redis DTOs. The HTTP publisher
atomically persists canonical request-v2/profile/result-v4, outbox and the immutable
execution registry; JPA is flushed before JDBC fences, not independently committed.
Initial canonical scope excludes visual/course/title sessions and requires audio
quality PASS. The legacy request factory is unchanged. Public admission is still
disabled; routing intent is not readiness or permission to bypass admission.

`CanonicalCommittedResultReader` reads only visible current pointers with APPLIED
inbox/VERIFIED evidence proof, exact raw and JCS hashes, and matching execution
identity. `CanonicalCompletionEligibility` connects this evidence to the existing
completion reader and rechecks it under the session writer lock. Only COMPLETED /
INLINE / ACCEPT / deliverable normal or fallback/no-candidate results can complete
the learning procedure. Title grading explicitly rejects canonical scores via the
existing score-unavailable branch. Canonical claim/heartbeat also checks registry
visibility. These are local, un-deployed implementations, not runtime validation.

Remaining: public view/actions, FE consumption, same-session rerecord recovery
after non-ACCEPT, media/segment post-processing, RunPod execution/delivery/readiness,
key injection and deployment. Existing recording mutation accepts only RECORDING/
UPLOADING; ANALYZING after non-ACCEPT cannot yet rerecord. Course progress has no
analysis-result binding and is not enabled for initial canonical scope. No schema,
migration, key or runtime setting was changed in this continuation. Final Java
compile/resources succeeded (24 seconds); shared schema bytes and legacy factory
source were statically compared. No tests, SQL/JPQL execution or API QA were run.

Earlier snapshots below describe their own stage, not the latest remaining work.

2026-10-01 latest local continuation: the result URL now routes schema v4 to
CanonicalCallbackDocument/Inbox/VerifierWorker/Committer. V34 adds a durable raw
callback validation inbox, immutable result bytes plus JSON and typed metadata,
current-result fields and targeted cache effects. This is not deployed. Public
admission/readiness remain disabled; the public canonical view/business gates
and the admitted publisher are still unconnected. The media-cleanup hook was
denied by safety review and awaits separate user approval; results with a visual
object or old segments are explicitly NOT_READY until that post-processing is
resolved. Nothing was deleted. See the shared callback-queue contract below.

Additional contract-preserving refactor: `CanonicalArtifactLoader` now owns the
two verification workers' ordered manifest/artifact read loop. Each worker still
owns its claim, live fence, semantic verification, retry policy and dedicated lane.
`RunPodRequestBody` shares bounded internal HTTP body reading after the existing
controller authentication. Routes, DTOs, error/status codes, body limits, schemas,
migrations, admission and legacy/TTS behavior are unchanged by this extraction.
`compileJava processResources --offline --no-daemon` passed (24 seconds); no tests
or runtime/API/SQL operations were performed.

Deployment remains separate: even with admission disabled, current result/cache
readers depend on V32 and the entity depends on V34. Do not ship the new JAR before
the required migrations. Public canonical view, completion/progress eligibility
and actual FE entry points remain incomplete. Static review also found the older
four-argument controller construction in `InternalRunPodAnalysisControllerTest`;
developer-owned test maintenance is still required, and tests were not modified
or run. The sections below retain earlier implementation/design snapshots and
must not override this current local status.

The 2026-09-30 compatibility foundation implements request v1/v2 parsing and a disabled
public v4 gate. It does not implement result v4 ingestion, its database model, receipt
verification, or a canonical public reader. The existing result reader accepts only HTTP
result v1/v2/v3. This proposed interface must not be advertised as installed support.

2026-10-01 continuation: evidence registration/status endpoints and the private
version-pinned B2 reader are now implemented locally. V33 persists immutable raw
manifests and PENDING verification jobs. Neither V32 nor V33 is deployed in this
execution. Frozen semantic verification/queue consumption is implemented in
CanonicalEvidenceJobs/Worker/SemanticVerifier; result ingestion and public
projection remain unconnected. The new worker issues VERIFIED only after exact
version readback, the offline frozen semantic check and a final execution fence.
It is not running in production yet. See
[the shared evidence contract](../contracts/runpod_http_control_v1_2.md) and the
[dedicated reader settings](canonical-evidence.env.example). The application key
is not injected into the running service yet. AWS has a separately staged
Python 3.12 verifier under `/opt/alpha-canonical`; its `--check-installation`
readiness passed on 2026-10-01 04:03 UTC without a job or inference. Java compilation/package checks
are not operational evidence or manual QA acceptance.

2026-10-01 contract-preserving refactor: the private `EvidenceFailure` classification
now belongs to the canonical infrastructure package rather than being nested in the
B2 reader. The reader, offline verifier and worker retain the same reason codes,
retryability and fencing. No public DTO, route, schema, migration or admission gate
changed. `compileJava processResources --offline` passed; no tests, inference or
deployment were performed. The AI offline bundle now also requires the extracted
`canonical_codec.py`; deploy a new complete bundle, not individual replacement files.

Proposed domain port, after the shared result-v4 contract and frozen sources are reviewed:

```java
public interface CanonicalResultAdapter {
    CanonicalResultWrite adapt(
            ValidatedCanonicalResult result,
            CanonicalResultExecutionContext execution,
            VerifiedEvidenceReceipt evidence
    );
}
```

These are proposed type names, not existing Java classes or validated frozen shapes.
The interface is intentionally not added to runtime source until its input/output types
can be defined from the actual frozen contract. It must not use `Map<String, Object>`,
arbitrary `JsonNode` payloads, or the legacy `AnalysisWorkerResult` as a substitute.

| Proposed type | Responsibility and construction boundary |
| --- | --- |
| `ValidatedCanonicalResult` | Produced only by a dedicated v4 parser/semantic validator. Keep callback event/request/execution identity and payload digest alongside typed canonical decision, adapter and generation state. Define frozen nested fields from reviewed source and schema; do not synthesize core data on pre-core failure. |
| `CanonicalResultExecutionContext` | Future application-owned context combining the persisted `CanonicalExecutionBinding` with a freshly locked current analysis, claimed worker and lease. The registry snapshot itself does not establish current authorization, claim ownership or result readiness. No caller-selected profile or object storage target. |
| `VerifiedEvidenceReceipt` | Constructed by the Backend evidence-verification boundary after checking execution association, receipt identity, immutable object versions, original byte SHA-256 and byte counts against the configured private store. A Python storage adapter or caller URL is insufficient. |
| `CanonicalResultWrite` | Validated domain write intent for the dedicated canonical store. Separates job terminal status, original core/input decision, adapter state and generation outcome; preserves candidate order, opaque scoped IDs, G2P roles, MFA facts and verified references. It is neither a JPA entity nor a public response DTO. |

The adapter is a pure conversion/consistency boundary: no network I/O, GPT calls, object
uploads, repository writes, public projection or implicit fallback. Binding, selection,
generation and evidence contradictions reject the callback with a typed validation
failure; they must not be normalized into a different canonical decision. Raw byte hashes
and the callback JCS digest have separate meanings and are never interchangeable.

The application callback service owns authentication, execution/claim/deadline fencing,
event conflict and duplicate handling, and the transaction. Its canonical writer must
commit result state, evidence association and required lifecycle outboxes atomically.
Acknowledgment follows the commit; a duplicate must not repeat storage cleanup or lifecycle
effects. The public reader separately applies its allowlist, including failed jobs with
valid retained core evidence, without exposing private keys, original errors or traces.

User decision (2026-09-30): retain original evidence indefinitely, with indefinite physical
deletion grace after cancellation or member deletion and no automatic evidence deletion.
This retention choice does not authorize public access after cancellation or member deletion;
execution fencing and access revocation still apply. The concrete private B2 bucket/prefix
and readiness approval remain unresolved. RunPod storage configuration is documented by
the main integration workstream; this Backend foundation does not configure or upload to B2.

Remaining implementation dependencies are Backend semantic validation of the finalized
preflight result-v4 schema/serializer, typed frozen mapping, Backend result/receipt
migrations and writer/verification, private bucket/prefix
configuration, lifecycle access revocation, public v2 projection, training/title eligibility,
retry and cancellation behavior, and developer-performed end-to-end acceptance. No live
uploads, retention scheduler, result-v4 fixtures, tests or QA artifacts are part of this
foundation. Canonical readiness and admission stay disabled until those dependencies are
implemented and verified by the responsible developers.

## Implemented next slice: immutable execution registry

Migration inventory at Backend `b48db988e7485943097acaa4c71308ef306175a8` ends at V31.
New `V32__create_canonical_execution_registry.sql` adds only
`analysis_canonical_executions`, its unique keys and immutability triggers. No existing
table is altered, no legacy data is backfilled, and no migration has been applied here.

The implemented `CanonicalExecutionRegistry` port and JDBC-backed adapter register a
snapshot from an already persisted request-v2 outbox. All identity, profile, version,
request JCS digest, script/audio hashes and deadline fields come from the validated
stored request. Private-content payloads use the existing JPA decryption accessor; the
registry does not copy scripts, private ciphertext, media keys or source bytes.

Registration locks analysis then outbox, checks the current request/execution,
PENDING/PROCESSING state, selected undeleted recording, ANALYZING session, active
non-deleted user, undeleted custom content, registered recording/content/audio identity, and matching unexpired
execution deadline. Failed delivery rows are rejected. Identical active registration
returns the existing snapshot; reused request/execution identity with changed immutable
fields fails without replacing data. This method is not a receipt verifier or callback
admission check and does not assert a worker lease.

`findCurrentForOwner` joins the registry to the live analysis request/execution and
recording/content/owner relations. It excludes canceled sessions/analyses, withdrawn or
suspended users, deleted custom content, deleted or deselected recordings, and prior attempts after retry. It
can return the current binding for a failed/expired job because attempt identification
does not assert successful inference or permission to run. It never falls back to
retained history and has no public controller, historical reader or cache.

The registry uses scalar IDs without foreign keys to mutable application tables, so
retention cannot cascade-delete these rows or obstruct existing recording/member
lifecycle operations. UPDATE, DELETE and TRUNCATE are rejected by PostgreSQL triggers.
There is no registry delete method, retention scheduler, public endpoint or automatic
registration caller. Retry naturally changes the live pointer while retaining older
snapshots; cancellation/member deletion revokes current visibility through live joins.
Existing recording/video deletion queues, cleanup and storage settings are untouched.

This boundary is deliberately disconnected from the legacy publisher, callbacks and
readiness probes. A future v2 admission transaction must persist its outbox and register
the binding atomically before dispatch. Result/receipt tables, verified receipt status,
result-v4 parsing and public result APIs remain unimplemented pending the reviewed
contracts and verifier. The explicit canonical admission and readiness flags remain false.
Compilation and static source review do not establish SQL migration or runtime correctness.

## Shared schema assets: packaging only

The six shared assets in `docs/contracts` are:

- `runpod_analysis_request_v2.schema.json`
- `runpod_http_control_v1_2.schema.json`
- `runpod_result_v4.schema.json`
- `runpod_canonical_parent.schema.json`
- `runpod_canonical_input.schema.json`
- `runpod_canonical_output.schema.json`

Root `.gitattributes` marks these files `-text` to preserve shared contract bytes across
checkouts. The existing `processResources` rule copies `runpod_*.schema.json` to
`build/resources/main/contracts`; packaging checks compare all six byte SHA-256 values
with the shared AI assets and the Backend sources. These checks do not validate callback
semantics, execute inference or migrate the database.

Copying the finalized preflight result and frozen schema assets does not install a
Backend v4 parser, semantic validator, writer, verified receipt store or public reader.
The legacy result parser still selects `runpod_result_v1.schema.json` for HTTP result
v1/v2/v3. A separate schema-v4 route now uses `runpod_result_v4.schema.json`, decimal
parsing and frozen semantic validation against originals outside the HTTP/DB lock.
The current readiness response does not yet advertise result-v4 support because
canonical consumers and post-processing are incomplete. Canonical admission remains disabled.

New-contract UTC timestamp corrections are maintained in the shared new assets. Existing
v1 schema bytes remain unchanged; the reported legacy timestamp-pattern issue and any
production revision drift require a separate compatibility review. Packaging is not
evidence that deployed legacy validation has been corrected.
