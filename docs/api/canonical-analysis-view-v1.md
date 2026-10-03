# Canonical public view v1 — local implementation

2026-10-03: [scored-H5 점수 계약](../canonical-scoring-20261003.md)을 추가했다. 새 하네스는 숫자 overallScore/rubricRevision/9개 criteria를 반환한다. 아래 점수 null 고정 설명은 기존 S7/H5 결과에 적용한다. 새 채점 실패는 UNSCORABLE이며 완료할 수 없다.

2026-10-01. Implemented in this worktree; **not deployed or runtime-validated**.
Admission/readiness remain off. This GET does not enable submission, create a job,
run AI/GPT, register evidence or read B2. V32–V34 must be installed before deploying
the expanded Backend. Existing analysis/result/segments/TTS routes are unchanged.

## Endpoint and visibility

`GET /api/v2/analyses/{analysisId}` requires the existing user JWT. There is no
request body, profile header or caller-selected execution ID. IDs must be positive
JavaScript-safe integers. The response uses the existing `ApiResponse` envelope:
`result`, localized `message`, `data`, and `code` on an error. Success and
controller-handled errors set `Cache-Control: no-store`.

| Status | Meaning |
|---|---|
| 200 | Current canonical PENDING/PROCESSING/COMPLETED/FAILED view |
| 400 `INVALID_ANALYSIS_ID` | Malformed, nonpositive or out-of-range path ID |
| 401/403 | Existing security-filter authentication/authorization rules |
| 404 `RESOURCE_NOT_FOUND` | Missing, another owner, deselected/deleted recording, canceled session/analysis, inactive/deleted user or deleted custom content |
| 404 `CANONICAL_ANALYSIS_NOT_FOUND` | Visible owned legacy execution only; the sole canonical-to-legacy fallback signal |
| 409 `CANONICAL_ANALYSIS_CHANGED` | Current request/execution/status/pointer changed during the read; discard and re-read |
| 503 `CANONICAL_RESULT_UNAVAILABLE` | Missing/inconsistent verification proof, invalid projection, unsupported profile or dependency failure; never fallback to cached/legacy results |

The query uses READ_COMMITTED, current ownership/execution registry and uncached
visibility checks before and after projection. These checks do not revoke already
downloaded copies or prevent a subsequent mutation after the response. Commands
still recheck authorization and state under their own locks.

## Public data allowlist

The typed `CanonicalAnalysisView` model defines this contract. There is no raw
`JsonNode`, arbitrary object payload or private callback in the response type.

| Field | Meaning |
|---|---|
| `schemaVersion` | `voice-coaching.canonical-analysis-view.v1` |
| `analysisId`, `recordingId` | Current positive safe integer database IDs |
| `requestId`, `executionId` | Complete UUIDs from the immutable current registry |
| `jobStatus` | Existing PENDING/PROCESSING/COMPLETED/FAILED axis |
| `analysisProfile` | `CANONICAL_FROZEN_20260928_V4` |
| `canonicalAnalysis` | Null before a valid core; otherwise the canonical allowlist below |
| `serviceFailure` | Null unless FAILED; fixed `origin`, `code`, `stage` only |
| `actions` | Server-computed command availability and reason arrays |

`canonicalAnalysis` preserves `representation`, `decision` (`status`, nullable
`reason_code`, nullable `stage`), `coreStatus`, `feedbackDeliveryAllowed`,
`pronunciationFeedbackSource`, nullable `selection`, nullable `coaching`, `score`
and `visual`. REJECT/INCONCLUSIVE are not successful pronunciation judgements.
RETAINED_ONLY remains FAILED, with selection/coaching null, not “no issue”.

Selection keeps `attemptScope`, coverage, review reason counts and ordered
candidates. Candidate fields are candidateId/evidenceIds, expectedPhone,
observedCandidate, expectedRole, roleSemantics, repetitionCount, claimScope,
disposition, presentationState, guidance and ordered facts. Facts keep evidenceId,
expectedIndex, localizationStatus and location: word/wordIndex, original MFA
word/phone start/end seconds and `MFA_LOCATION_ONLY`. No ID shortening, sorting,
millisecond conversion, source-audio seeking or articulation/speed inference occurs.
MFA values use BigDecimal in Java; browser finite-number/safe-integer bounds are
checked before returning. Private original bytes remain authoritative.

Coaching preserves its actual branch shape:

- READY: schemaVersion/adapterStatus/generationStatus, ordered candidate/expression
  items, dispatchAttempts, nullable fallbackReason, false natural-language-complete
  verification flag and false visual corrective-claim flag.
- GLOBAL_REJECT/GLOBAL_INCONCLUSIVE/GLOBAL_SYSTEM_FAILURE/NO_PERMITTED_COACHING_CONTENT:
  only schemaVersion/adapterStatus/generationStatus, empty items and zero attempts.
- FAIL_CLOSED: the non-dispatched empty branch plus its fixed errorCode.

Private `coreInputValidation`, raw inputValidation/diagnostics, sourceIdentity,
evidence pointers, receipt/manifest/version/object keys, script/audio/core hashes
and signed URLs are not exposed. Nulls required by this public contract are emitted
explicitly; branch-specific absent fields are not added as null placeholders.

Score remains `{overallScore:null, validity, reason:NO_APPROVED_SCORING_CONTRACT}`.
Visual remains NOT_CONNECTED / correctiveClaimsAllowed=false. No new score or
personal visual/prosody coaching is generated by this read.

## Failed and unfinished work

PENDING/PROCESSING returns canonicalAnalysis=null and serviceFailure=null; pending
is not INCONCLUSIVE. A committed pre-core failure can also have canonicalAnalysis=null.
For Backend failure before callback, current profile/registry identify the attempt:

| Stored failure code | Public serviceFailure |
|---|---|
| `analysis_execution_timeout` | BACKEND / ANALYSIS_EXECUTION_TIMEOUT / EXECUTION |
| `runpod_analysis_request_delivery_failed` | BACKEND / ANALYSIS_DELIVERY_FAILED / DISPATCH |
| Other non-canceled Backend failure | BACKEND / ANALYSIS_FAILED / EXECUTION |

Never echo arbitrary historic failureReason, exception text, or prior-attempt core.
A COMPLETED row without its current committed, verified result is unavailable,
not a fabricated empty result. Canonical committed reads require matching APPLIED
inbox proof, applicable VERIFIED receipt, original byte/JCS digests and current IDs.

## Actions and remaining integration

All four booleans have corresponding `unavailableReasonCodes` arrays named retry,
rerecord, complete and regenerate. Allowed means an empty array; denied has at
least one code. This is a read-time hint, not a new authorization grant.

- canRetry uses FAILED, the shared existing maximum of 3 user retries, session
  state, initial standalone-audio scope, admission/configuration and per-user
  concurrency. While canonical admission is off, retry is false with
  ANALYSIS_INTEGRATION_UNAVAILABLE. A future enabled retry still needs the existing
  consent body and explicit v4 header; this GET never silently opts into legacy.
- canComplete uses the same current-result eligibility as the actual completion
  command plus session state: verified deliverable INLINE/ACCEPT, including
  deterministic fallback or no permitted candidate, never non-ACCEPT/failure.
- canRegenerate is false with FEEDBACK_EVIDENCE_UNAVAILABLE.
- canRerecord respects actual recording mutability and the shared
  `CanonicalRerecordEligibility` policy below. Pending returns
  ANALYSIS_ALREADY_RUNNING; an inapplicable terminal result in ANALYZING returns
  CANONICAL_RERECORD_NOT_APPLICABLE; closed session states return INVALID_SESSION_STATE.

## Same-session re-recording (local implementation)

No new endpoint, request field, consent bypass or profile header is added to media
upload/registration/selection. The existing sequence remains:

1. `POST /api/training-sessions/{sessionId}/recordings/upload-url` with the existing
   media declaration. Under the session and current selected-analysis locks, a
   verified current INLINE/COMPLETED REJECT or INCONCLUSIVE result in initial
   standalone-audio scope can move ANALYZING to UPLOADING. Its matching global
   coaching branch must be NOT_DISPATCHED, retained and free of service failure.
   ACCEPT, FAILED, pending, legacy and unverified results do not reopen a session.
2. Upload to the returned URL, then register via the existing recordings POST.
   Upload-intent reservation, ownership, active user, declared bytes, normalization
   and quality checks remain in place. This creates a new recording/attempt row.
3. Select the new PASS recording via the existing PATCH select route. The writer
   repeats ownership/session/deletion/quality checks under the session lock.
4. Send the existing analyze request/consent and the explicit v4 header for canonical
   processing. A new recording obtains a new analysis/request/execution. Headerless
   requests retain legacy behavior; disabled v4 admission still returns 503.

The upload transition and intent issuance share a transaction and roll back on
failure. Duplicate upload-URL requests in UPLOADING keep the existing semantics.
Abandoning an upload leaves the session UPLOADING, permitting another upload; it
does not retry or delete the previous result. The old selection remains until the
explicit select call. Original analysis/callback/receipt/evidence rows are not
modified or deleted, and a deselected canonical result is hidden by existing read
fences. Re-selecting an analyzed recording does not bypass ANALYSIS_ALREADY_REQUESTED.
FAILED still uses the existing retry command/limit; REJECT/INCONCLUSIVE remain
COMPLETED and are not rewritten as FAILED. Completed/canceled sessions need a new
session instead of reopening. No score, completion or course/title credit is granted.

Analyze/retry now acquire the session selection lock after the existing admission
user lock and before reading their source. A concurrent selection cannot make the
published request refer to a previously selected recording. Recovery locks session
then analysis; callback workers do not acquire a session mutation lock in reverse.
The policy is also used for GET actions, but only commands mutate state. Recovery
does not require admission to be enabled: upload readiness is not AI readiness.

Developer acceptance remains required for non-ACCEPT upload/register/select/new
analysis, rollback/abandoned uploads, concurrent selection/analyze/retry/cancel,
legacy/ACCEPT/FAILED/closed-session rejection, and old-result hiding/preservation.
This implementation has not been exercised against a live DB or HTTP server.

Standalone FE parser/client source was compared to these field/branch names.
Actual FE entry points, retry/re-record commands, cache/history integration and
developer acceptance remain incomplete. No FE URL, deployment or browser QA is
claimed by this source-only implementation.
