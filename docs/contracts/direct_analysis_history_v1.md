# Direct analysis history v1

2026-10-05 implementation; not deployed. Additive migration V40; controller default OFF (`analysis.direct-history.enabled`, environment `ANALYSIS_DIRECT_HISTORY_ENABLED`). Existing v5 dispatch/completion is unchanged by these new classes.

RunPod's public analysis API is anonymous by user decision. This Backend is not an admission gate or a prerequisite for displaying the result. It stores background events after RunPod has published the result.

| Method / path | Boundary | Behavior |
|---|---|---|
| POST `/api/internal/ai/direct-analysis-history` | Existing RunPod callback bearer | <=1MiB event, transaction commit before 202 |
| POST `/api/direct-analysis-history/{jobId}/link` | Existing LoginUser | Body `{historyClaim: string}`; attach the account using its SHA256 |
| GET `/api/direct-analysis-history/{jobId}` | Existing LoginUser | NOT_LINKED/PENDING/CONFLICT/RESULT_RECEIVED/SAVED |
| GET `/api/direct-analysis-history` | Existing LoginUser | Up to 100 matching linked results, newest first |

Event schemaVersion is `voice-coaching.analysis-history-event.v1`. Fields: UUID jobId/executionId/clientAttemptId, historyClaimSha256, audioSha256, scriptSha256, scriptText, nullable contentId, resultSha256, result object, nullable archive. Source identity in the result must match execution and input digests. Result hash describes the original RunPod JSON bytes; it is authenticated producer provenance, not a hash of Jackson's reserialization. The Backend does not rerun scoring.

The first event can omit archive. A later identical result fills archive with artifact kind/objectKey/versionId/sha256/byteSize entries and recordingObjectKey. Conflicting execution/claim/result/script/content/archive is 409. Network retries are idempotent. The worker sends authenticated events; the anonymous browser cannot write this endpoint.

`direct_analysis_history`: UUID job primary key, execution ID, claim/result digests, result JSON text, script, nullable content reference, nullable archive JSON, timestamps. `direct_analysis_history_links`: user FK (users, cascading link deletion), job UUID, claim digest, timestamp; primary key(user,job). Link may arrive before the event. A join only exposes matching digests. Raw claim is 32 random bytes encoded as 43 base64url characters and is never returned by RunPod's public result. No browser-provided userId is trusted.

This is a separate independent-practice history collection, not a new training_sessions/analysis_results row. Course/exam completion, streaks, canonical reservations and original history aggregates are not updated. The original voice object goes to the existing recording bucket under the configured direct prefix; evidence goes to B2. Account unlinking does not yet purge the producer inbox/archive; deletion/retention orchestration needs a separate production check.

V40 adds tables only; a previous JAR ignores them. Rollback keeps rows for later replay. Validate the migration against production PostgreSQL and review storage retention before enabling. Java compileJava passed; no tests, DB migration or deployment were run. The worktree also carries pre-existing Native DRAFT changes: do not merge/deploy that whole worktree as though only this additive feature changed.
