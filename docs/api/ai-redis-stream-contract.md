# Backend-AI Redis Stream Contract

## Purpose and boundary

VC-BE and the intelligentAI worker exchange asynchronous audio-analysis work through
Redis Streams. There is no backend-to-AI HTTP callback and the worker does not expose
a public API. Client authentication, session ownership, storage metadata, persistent
job state and result retrieval remain owned by VC-BE.

```text
Client
  -> POST /api/training-sessions/{sessionId}/analyze
  -> PostgreSQL: analysis_results=PENDING + analysis_request_outbox
  -> Redis: analysis:request:v1
  -> intelligentAI worker
  -> Redis: analysis:result:v1
  -> VC-BE result consumer -> PostgreSQL result + segments
  -> GET status/result/segments
```

The request outbox is the durable boundary between the PostgreSQL request
transaction and Redis `XADD`. Delivery is at-least-once; every consumer must be
idempotent.

## Redis topology and environment

Stream traffic uses a dedicated Redis endpoint. It must not share the endpoint used
for Spring Cache because cache maintenance may delete keys without preserving pending
job messages.

| Variable | Required | Description |
| --- | --- | --- |
| `ANALYSIS_STREAM_ENABLED` | Y in production | Enables VC-BE Stream dispatcher and result consumer. Default is `false`. |
| `ANALYSIS_REDIS_HOST` | Y | Private Redis host for analysis messages. |
| `ANALYSIS_REDIS_PORT` | Y | Redis port. |
| `ANALYSIS_REDIS_USERNAME` | N | Redis ACL username. |
| `ANALYSIS_REDIS_PASSWORD` | Y | Secret-manager injected Redis ACL password. Never log or commit it. |
| `ANALYSIS_REDIS_SSL_ENABLED` | Y | Must be `true`; Stream-enabled startup otherwise fails closed. |
| `ANALYSIS_REDIS_CONNECT_TIMEOUT` | Y | TCP connect bound, default `PT5S`. |
| `ANALYSIS_REDIS_COMMAND_TIMEOUT` | Y | Redis command bound, default `PT5S`; must exceed result `BLOCK`. |
| `ANALYSIS_REDIS_SHUTDOWN_TIMEOUT` | Y | Lettuce shutdown bound, default `PT1S`. |
| `ANALYSIS_REQUEST_STREAM` | Y | Default `analysis:request:v1`. |
| `ANALYSIS_RESULT_STREAM` | Y | Default `analysis:result:v1`. |
| `ANALYSIS_RESULT_CONSUMER_GROUP` | Y | Default `backend-analysis-result-workers`. |
| `ANALYSIS_RESULT_CONSUMER_NAME` | Y | Unique pod/host instance name. |
| `ANALYSIS_RESULT_DLQ_STREAM` | Y | Default `analysis:result:dlq:v1`. |
| `ANALYSIS_CANCELLATION_KEY_PREFIX` | Y | Default `analysis:canceled:v1:`; only an opaque request event UUID is appended. |
| `ANALYSIS_REQUEST_INDEX_KEY_PREFIX` | Y | Default `analysis:request-index:v1:`; maps one request event to its idempotent Stream ID. |
| `ANALYSIS_CANCELLATION_OUTBOX_POLL_INTERVAL` | Y | Durable cancellation dispatch interval, default `PT1S`. |
| `ANALYSIS_RETENTION_AGE` | Y | Minimum terminal DB evidence age before outbox/marker cleanup, default `PT1H`. |
| `ANALYSIS_RETENTION_POLL_INTERVAL` / `ANALYSIS_RETENTION_BATCH_SIZE` | Y | Cleanup schedule and bounded batch, defaults `PT5M` / `100`. |
| `ANALYSIS_STREAM_MAXIMUM_PAYLOAD_BYTES` | Y | UTF-8 request/result payload cap, default `65536`. |
| `ANALYSIS_RESULT_DLQ_MAXIMUM_LENGTH` | Y | Approximate result DLQ cap, default `10000`. |
| `ANALYSIS_PENDING_CLAIM_IDLE` | Y | Minimum pending idle time before reclaim; default `PT5M`. |
| `ANALYSIS_STREAM_MAX_RETRIES` | Y | Dispatch/result retry cap; default `3`. |
| `ANALYSIS_MAX_CONCURRENT_PER_USER` | Y | DB-serialized per-user concurrent job cap; default `3`. |
| `ANALYSIS_EXECUTION_TIMEOUT` | Y | Maximum time a generation may remain PENDING/PROCESSING; default `PT15M`. |
| `ANALYSIS_TIMEOUT_SWEEP_INTERVAL` | Y | Stale-generation sweep interval; default `PT1M`. |
| `ANALYSIS_TIMEOUT_SWEEP_BATCH_SIZE` | Y | Maximum rows locked by one timeout transaction; default `100`. |
| `ANALYSIS_AUTHORIZATION_KEY_ID` | Y | Active HMAC key identifier; `[A-Za-z0-9._-]`, max 100. |
| `ANALYSIS_AUTHORIZATION_SIGNING_SECRET_BASE64` | Y | Secret-manager value decoding to at least 32 bytes. Never log or commit it. |
| `ANALYSIS_CONSENT_POLICY_REVISION` | Y | Exact active consent revision accepted from the client. |
| `ANALYSIS_AUTHORIZATION_GRANT_TTL` | Y | Short-lived grant TTL, default `PT5M`, maximum `PT10M`. |
| `OBJECT_STORAGE_ENABLED` | Y | Must be `true` whenever Stream analysis is enabled. |
| `OBJECT_STORAGE_BUCKET` / `OBJECT_STORAGE_REGION` | Y | Private S3-compatible recording bucket and signing region. |
| `OBJECT_STORAGE_ENDPOINT` | Provider-specific | HTTPS-only endpoint override for NCP/R2/MinIO-compatible deployments. |
| `OBJECT_STORAGE_PATH_STYLE_ACCESS` | N | Enables path-style access only when the provider requires it. |
| `OBJECT_STORAGE_RECORDINGS_PREFIX` | Y | Private relative key prefix; default `recordings/`. |
| `MEDIA_NORMALIZATION_ENABLED` | Y | Must be `true` with Stream analysis; enables backend-owned probe, decode, technical QC and canonical WAV upload. |
| `MEDIA_NORMALIZATION_WORKSPACE_ROOT` | Y | Absolute owner-only temporary workspace root. |
| `MEDIA_NORMALIZATION_FFMPEG_BINARY` / `MEDIA_NORMALIZATION_FFPROBE_BINARY` | Y | Absolute reviewed executable paths. |
| `MEDIA_NORMALIZATION_PROCESS_TIMEOUT` | Y | Per-process timeout, default `PT30S`, maximum `PT2M`. |

The Redis port is private-network only. Do not open it to the internet, transmit a
Redis URL/password in messages, or use the ordinary cache endpoint for Stream data.
TLS peer verification remains enabled. A private Redis CA must be installed in the
JVM trust store supplied to VC-BE; disabling certificate verification is unsupported.
The Backend Redis ACL needs the existing request/result Stream commands plus
`GET`, `SET`, `DEL`, `XRANGE`, and `EVAL` for request indexing, cancellation and
retention. The AI ACL needs `EXISTS`, `DEL`, and `EVAL` in addition to its Stream
commands. Fixed Lua scripts atomically publish/index a request, check cancellation
before result `XADD`, or combine `XACK` with `XDEL`.

## Stream ownership

| Stream | Producer | Consumer group | Consumer | ACK owner |
| --- | --- | --- | --- | --- |
| `analysis:request:v1` | VC-BE outbox dispatcher | `analysis-workers` | intelligentAI worker | AI worker after terminal result `XADD` |
| `analysis:result:v1` | intelligentAI worker | `backend-analysis-result-workers` | VC-BE result consumer | VC-BE after DB transaction commits |
| `analysis:request:dlq:v1` | intelligentAI worker | operator-only | restricted operator tooling | n/a |
| `analysis:result:dlq:v1` | VC-BE result consumer | operator-only | operator tooling | n/a |

Cancellation is a shared Redis key rather than a consumer-group message because every
worker must observe it. VC-BE first commits one idempotent row in
`analysis_cancellation_outbox`, then retries `SET analysis:canceled:v1:<eventId> 1`
until it succeeds. The tombstone has no TTL: expiring it while an old request remains
in a Stream or PEL could re-authorize compute after revocation. It is deleted only by
the DB-aware acknowledged-message retention workflow. The key contains no user,
session, recording, object, or consent identifier.

Normal request/result Stream entries have exactly one string field, `payload`,
containing UTF-8 JSON. Producer and consumer reject unknown fields and unsupported
`schemaVersion` values. DLQ entries are restricted operator records: they contain
`sourceStreamId`, `failureCode`, and, when an original payload existed, that raw
`payload` for deliberate recovery.
Payload size is measured as UTF-8 bytes before JSON decoding. An oversized result is
never copied into the DLQ body; only its source Stream ID and stable failure code are
retained. Result DLQ trimming is approximate and bounded. Request/result Streams do
not use age- or length-based trimming. The AI worker atomically `XACK`+`XDEL`s a
request only after its terminal result/DLQ write, and VC-BE atomically `XACK`+`XDEL`s
a result only after its PostgreSQL transaction commits. This removes acknowledged
entries without risking pending or unpersisted work.
VC-BE also rejects an oversized request payload before writing its durable outbox row;
the dispatcher repeats the check immediately before Redis I/O as defense in depth.

## Request payload: `voice-coaching.analysis-request.v4`

```json
{
  "schemaVersion": "voice-coaching.analysis-request.v4",
  "eventId": "4adfe173-0691-4e89-b94e-a5c5c5085826",
  "analysisId": 35,
  "contentId": 12,
  "promptRevision": "2026-09-02T00:00:00Z",
  "scriptText": "안녕하세요. 오늘 날씨가 좋습니다.",
  "scriptSha256": "lower-case sha256 hex",
  "audioObjectKey": "recordings/analysis-audio/<opaque-uuid>.wav",
  "audioSha256": "lower-case sha256 of the normalized WAV bytes",
  "mimeType": "audio/wav",
  "fileSizeBytes": 1234,
  "durationMs": 1200,
  "learningFocus": "PRONUNCIATION",
  "visualInput": {
    "objectKey": "recordings/analysis-video/<opaque-uuid>.mp4",
    "sha256": "lower-case sha256 of the canonical MP4 bytes",
    "mimeType": "video/mp4",
    "fileSizeBytes": 4567,
    "consentReceiptSha256": "lower-case sha256 hex",
    "consentPolicyRevision": "voice-video-processing-consent-v1"
  },
  "authorizationGrant": {
    "grantVersion": "voice-coaching.analysis-authorization.v3",
    "keyId": "backend-2026-09",
    "requestEventId": "4adfe173-0691-4e89-b94e-a5c5c5085826",
    "analysisId": 35,
    "contentId": 12,
    "promptRevision": "2026-09-02T00:00:00Z",
    "scriptSha256": "lower-case sha256 hex",
    "audioObjectKeySha256": "lower-case sha256 hex",
    "audioSha256": "lower-case sha256 of the normalized WAV bytes",
    "mimeType": "audio/wav",
    "fileSizeBytes": 1234,
    "durationMs": 1200,
    "learningFocus": "PRONUNCIATION",
    "consentReceiptSha256": "lower-case sha256 hex",
    "consentPolicyRevision": "voice-analysis-consent-v1",
    "visualObjectKeySha256": "lower-case sha256 hex",
    "visualSha256": "lower-case sha256 hex",
    "visualMimeType": "video/mp4",
    "visualFileSizeBytes": 4567,
    "visualConsentReceiptSha256": "lower-case sha256 hex",
    "visualConsentPolicyRevision": "voice-video-processing-consent-v1",
    "issuedAtUtc": "2026-09-02T00:00:00Z",
    "expiresAtUtc": "2026-09-02T00:05:00Z",
    "purpose": "pronunciation_coaching",
    "dataCategory": "learner_voice_recording",
    "deleteOnCompletion": true,
    "remoteEgressAllowed": false,
    "signature": "lower-case HMAC-SHA256 hex"
  }
}
```

| Field | Required | Rule |
| --- | --- | --- |
| `schemaVersion` | Y | Exact value above. |
| `eventId` | Y | UUID; identifies this request generation. A retry gets a new value. |
| `analysisId` | Y | Existing `analysis_results.id`; positive integer. |
| `contentId` | Y | Existing trusted `practice_contents.id`; positive integer. |
| `promptRevision` | Y | VC-BE content revision snapshot. |
| `scriptText` | Y | Trusted content text, maximum 10,000 characters. |
| `scriptSha256` | Y | Lower-case SHA-256 of UTF-8 `scriptText`. |
| `audioObjectKey` | Y | Storage object key, never a URL or path. |
| `audioSha256` | Y | Backend-calculated SHA-256 of the canonical object. The worker must match it before inference. |
| `mimeType` | N | Registered media MIME type. |
| `fileSizeBytes` / `durationMs` | N | Non-negative registered metadata. |
| `learningFocus` | Y | `PRONUNCIATION`, `INTONATION`, or `BOTH`. Unsupported worker focus fails closed. |
| `visualInput` | N | All-or-none canonical MP4 reference and face-processing consent. It is consumed only after Seungun selects a phone. |
| `authorizationGrant` | Y | Signed, short-lived, same-request processing authority described below. |

Before this payload exists, VC-BE verifies that the upload key belongs to the
authenticated user and session, probes the real container and codecs, extracts exactly
one audio stream, writes 16 kHz mono signed PCM WAV, performs technical audio QC,
stores it under an opaque backend-only key, calculates `audioSha256`, and deletes
the client-uploaded source. The normalizer binds its source GET and DELETE to the ETag
and optional VersionId observed immediately before download, so a replaced upload fails
closed and is not accidentally deleted. A database rollback after canonical upload
triggers best-effort cleanup of every unregistered canonical audio/video object. For
consented video, VC-BE also strips container metadata,
canonicalizes the media to MP4 H.264/HEVC plus AAC, stores it under a second opaque
key, and binds its digest, size, MIME and face-consent receipt into the request.
MP4/QuickTime accepts H.264 or HEVC with AAC; WebM video accepts VP8 or VP9 with
Opus or Vorbis and is transcoded to the canonical MP4 contract. Unsupported or
ambiguous streams fail closed.

The payload intentionally excludes `userId`, `sessionId`, `recordingId`, file paths,
presigned URLs, and raw consent material. Both analyze and retry REST calls require
`{"accepted":true,"policyRevision":"..."}` from the authenticated owner. Before
issuing a job, VC-BE durably records an opaque consent receipt bound to the owner,
session, recording, request generation, policy revision, and normalized audio digest,
then signs that receipt into a five-minute grant. Session cancellation and account
withdrawal timestamp every still-active receipt as revoked. The v3 grant
binds request event, analysis/content/prompt, script digest, object-key digest,
normalized audio digest, MIME, size, duration, learning focus, policy, purpose,
data category, cleanup and egress policy. When visual input is present it additionally
binds every visual field and its separate face-processing consent. A retry receives
and persists a new request event, receipt and signature.

The signature is HMAC-SHA256 over the fields above in listed order, excluding
`signature`. Each line is `name:utf8ByteLength:value\n`; a null value is
`name:-1:\n`; booleans are lower-case. `issuedAtUtc` and `expiresAtUtc` use the exact
UTC JSON strings. The AI keyring selects the secret by `keyId` and compares the
signature in constant time. Unknown keys, signature/binding differences, a future or
expired window, a TTL over ten minutes, a policy mismatch, missing cleanup, or enabled
remote egress fail before object storage access. Legacy request v1-v3 and grant v1-v2 are unsupported.

The AI worker uses a restricted object-storage adapter for the configured bucket and
must inspect each object, condition the streaming GET on its ETag and optional VersionId,
verify the returned object identity, key, MIME type, registered size, locally calculated digest against
`audioSha256`, and cleanup
policy. VC-BE rejects a legacy URL or traversal-like key before publishing; the
request transaction rolls back instead of leaving a stranded pending analysis. A
worker deployment without an approved authorization, storage, and Seungun composition
must refuse startup before it consumes any Stream entry.

## Result payload: `voice-coaching.analysis-result.v3`

```json
{
  "schemaVersion": "voice-coaching.analysis-result.v3",
  "eventId": "e917fda8-3c4f-4b7e-9094-7a1706081f1b",
  "requestEventId": "4adfe173-0691-4e89-b94e-a5c5c5085826",
  "analysisId": 35,
  "status": "COMPLETED",
  "outcome": "COACHING_READY",
  "transcript": null,
  "sttConfidence": null,
  "sttModelName": null,
  "overallScore": null,
  "pronunciationScore": null,
  "intonationScore": null,
  "speedWpm": null,
  "speedStatus": null,
  "stressScore": null,
  "pauseScore": null,
  "strengthsText": null,
  "weaknessesText": null,
  "summaryFeedback": "목표 음소 ‘ㄱ’ 소리를 천천히 분리해 발음해 보세요.",
  "pronunciationEvidence": {
    "schemaVersion": "voice-coaching.pronunciation-evidence.v1",
    "selectedPhone": "ㄱ",
    "selectedExpectedIndex": 0,
    "selectedStartMs": 120,
    "selectedEndMs": 240,
    "detectorScore": 0.91,
    "operatingThreshold": 0.8,
    "scoreSemantics": "detector_ranking_score_not_calibrated_correctness_confidence",
    "evidenceState": "frozen_detector_threshold_passed"
  },
  "workerRevision": "worker-revision",
  "pipelineRevision": "pipeline-revision",
  "audioSha256": "lower-case sha256 hex",
  "segments": [],
  "visualSupplement": {
    "schemaVersion": "voice-coaching.visual-supplement.v1",
    "selectedExpectedIndex": 0,
    "evidenceRelation": "supports_upstream",
    "approvedClaimId": "lip.aperture.low",
    "rendererKey": "lip_aperture_hint",
    "upstreamPhoneAnchorRef": "lower-case sha256 hex",
    "supplementSha256": "lower-case sha256 hex"
  }
}
```

| Field | Required | Rule |
| --- | --- | --- |
| `schemaVersion`, `eventId`, `requestEventId`, `analysisId`, `status` | Y | Version, UUID lineage, and positive analysis ID. `requestEventId` must equal the current active request generation. |
| `status` | Y | `PROCESSING`, `COMPLETED`, or `FAILED`; `PENDING` is not a worker result. |
| `outcome` | completed only | Result v3 accepts only `COACHING_READY` and `COMPLETED_NO_ISSUE`; other enum values remain reserved until their evidence mapping is reviewed. |
| `visualSupplement` | N | Only an approved same-attempt action projection. Its selected index must equal Seungun's `pronunciationEvidence.selectedExpectedIndex`; it cannot create a diagnosis. |
| `failureCode`, `failureReason` | failed only | Stable code plus learner-safe reason (max 500 chars). Never include infrastructure exception text. Failed/processing results contain no transcript, scores, feedback, digest, or segments; a failure may retain worker/pipeline revision receipts. |
| `pronunciationEvidence` | coaching only | Required exactly when `outcome=COACHING_READY`. It preserves the same-attempt Seungun-selected phone, index, optional time range, threshold-passed ranking score and explicit non-confidence semantics. |
| transcript, score, strength/weakness, segment fields | N | The v3 production mapping requires these to remain `null`/empty because no approved Seungun mapping currently supplies them. Placeholder or independently inferred values are rejected. |
| revision/digest fields | completed | Worker/pipeline revision and lower-case audio SHA-256 are required provenance; object keys and digests are not exposed as public client API fields. |
| `segments` | all states | Must currently be an empty array. A future segment mapping requires a new reviewed schema. |

Legacy `voice-coaching.analysis-result.v1` and `v2` are rejected. Result-v3 deployment therefore
requires a quiesced rollout: stop new analysis admission and the AI worker, drain or
deliberately resolve all pending request/result entries, deploy Backend and worker,
then enable both together. Do not run an older result producer against the v3 consumer
or the reverse. The Redis stream name remains `analysis:result:v1`; it identifies the
transport channel, not the JSON schema version.

`PROCESSING` updates only the job state. It does not authorize the AI worker to ACK
the request; the request remains pending until the worker publishes a terminal
`COMPLETED` or `FAILED` result.

## Persistence and idempotency

- `analysis_results.active_request_event_id` identifies the active request generation.
  VC-BE ignores a late result whose `requestEventId` differs, including a result from
  a pre-retry attempt.
- A duplicate terminal result for the active generation does not overwrite an already
  terminal analysis or duplicate `analysis_segments`.
- The first accepted completed result atomically replaces the segments for its
  `analysisId`; a failed result stores stable failure code/reason.
- `analysis_request_outbox` persists request payloads before Redis I/O. Its final
  dispatch failure changes the matching analysis to `FAILED`.
- Request publication atomically creates the Stream entry and an opaque event-to-ID
  index. A retry after an uncertain DB commit returns the same indexed Stream ID
  instead of publishing duplicate work.
- Each outbox publish runs in its own bounded DB transaction. Redis failure therefore
  holds at most one outbox row lock for at most the configured command timeout.
- PostgreSQL is the permanent result store. Redis holds transport messages only; no
  audio blob, token, URL, raw exception, or user identifier is placed in a stream.
- Analysis admission takes a pessimistic lock on the owning user row before counting
  PENDING/PROCESSING jobs. This makes the per-user concurrent cap effective across
  backend instances rather than being an in-memory rate limit.
- The retention sweeper considers only protocol-v1 request outboxes whose analysis
  and outbox states are terminal and older than `ANALYSIS_RETENTION_AGE`. It verifies
  that the exact indexed request Stream entry is absent, requires any cancellation
  outbox to be published, then deletes the request payload row, cancellation row,
  request index, and tombstone. Legacy rows with no retention protocol marker are
  deliberately left for reviewed migration rather than guessed safe.

## Retry, reclaim, and dead letter handling

1. VC-BE retries pending outbox records with bounded backoff, up to
   `ANALYSIS_STREAM_MAX_RETRIES`.
2. A VC-BE result consumer does not ACK until `AnalysisResultIngestionService` commits.
3. Pending result messages idle for at least `ANALYSIS_PENDING_CLAIM_IDLE` are claimed
   by the configured consumer. This supports process restart and consumer loss.
4. A decodable result message whose delivery count reaches the retry cap first fails
   the matching active analysis with `analysis_result_retry_exhausted`, then is copied
   to the result DLQ with only `payload`, source stream ID, and stable failure code,
   and finally ACKed. A malformed message, missing payload, deleted source record, or
   unknown analysis is copied with a stable non-sensitive DLQ failure code and ACKed,
   then retained only in the DLQ for restricted operator recovery.
5. Operators inspect the DLQ using restricted tooling. Replaying a message requires a
   new deliberate Stream entry; do not edit a message in place.
6. The AI request consumer reclaims idle pending requests before reading new entries.
   It writes a terminal result before ACKing a valid request. A malformed or missing
   request payload cannot produce a normal result; the worker copies it to
   `analysis:request:dlq:v1` with only the source Stream ID, original payload, and a
   stable failure code, then ACKs it. Request-DLQ access and replay are restricted
   operator actions.
7. A DB sweeper changes a generation that exceeds `ANALYSIS_EXECUTION_TIMEOUT` to
   `FAILED`, prevents an unpublished outbox record from being dispatched, and revokes
   active processing consent for that session. It also persists a cancellation
   tombstone outbox row. A result that arrives after that point
   is ACKed as a duplicate and cannot revive the failed generation.
8. Session cancellation fails and clears every non-failed analysis result, deletes its
   derived segments, and prevents pending outbox dispatch before consent and media are
   revoked. History deletion and user withdrawal use the same cancellation path.
   Cancellation schedules tombstones for every previously issued request generation
   in the aggregate and deliberately wins even when a terminal result arrived just
   before the session transaction acquired the analysis lock. The AI worker checks a
   tombstone before work, polls it while the model runs, interrupts the isolated
   Seungun process when observed, and atomically rechecks it with result publication.

## Health and metrics

VC-BE exposes management endpoints on `127.0.0.1:9091` by default:

- `/internal/actuator/health` includes the dedicated analysis Redis readiness check.
- `/internal/actuator/prometheus` exports bounded-cardinality publish, ingestion,
  delivery-failure, DLQ, execution-timeout, cancellation-delivery, and retention
  cleanup/failure counters.

No user, session, recording, event, object key, payload, or exception is used as a
metric label or health detail. If the management address is changed from loopback,
the deployment must restrict the port to the health checker and metrics collector.

## Public API impact

Client APIs remain VC-BE REST endpoints:

- `POST /api/training-sessions/{sessionId}/analyze`
- `GET /api/training-sessions/{sessionId}/analysis/status`
- `POST /api/training-sessions/{sessionId}/analysis/retry`
- `GET /api/analyses/{analysisId}`
- `GET /api/analyses/{analysisId}/segments`

Completed result views add nullable `outcome` and `pronunciationEvidence`; worker
revisions, object keys, hashes, Stream IDs, and internal failure codes are not public
API response fields. The current runtime accepts only `PRONUNCIATION`. `INTONATION`
and `BOTH` requests return `422` before consent issuance or analysis state creation.

Both POST endpoints above require this request body; the server rejects missing,
false, blank, or stale consent before it creates/publishes analysis state:

```json
{"accepted": true, "policyRevision": "voice-analysis-consent-v1"}
```
