# AI-API Branch Review

## Purpose

This document reviews the current `AI-API` branch against:

- the current local DB backup shared by the user: `voicebackup 09.03.sql`
- the project DB specification: `docs/database/schema.md`
- the existing `develop` branch architecture conventions
- the AI Redis Stream contract: `docs/api/ai-redis-stream-contract.md`

The SQL backup is treated as the current DB state reference, not as a project
specification. Project specifications should remain under `docs/`.

## Summary

The `AI-API` branch is closer to a production-grade AI analysis pipeline than a
minimal backend-AI connection test.

It introduces:

- Redis Stream request/result transport
- DB outbox dispatch
- result consumer retry and DLQ handling
- cancellation tombstones
- processing consent ledger
- S3-compatible object storage adapter
- media normalization through ffmpeg
- operational metrics and health checks

This direction is architecturally coherent for production, but it does not match
the current DB backup. The current DB backup appears to be earlier than the
schema expected by this branch.

## Current DB Backup Gap

Based on `voicebackup 09.03.sql`, the following core tables were detected:

- `analysis_results`
- `analysis_segments`
- `voice_recordings`
- `flyway_schema_history`

The backup also contains:

- `analysis_results.speed_status`
- `chk_analysis_results_speed_status`
- `chk_analysis_results_status`
- `uq_analysis_results_recording`

The following `AI-API` branch structures were not detected in the current DB
backup:

- `analysis_request_outbox`
- `analysis_cancellation_outbox`
- `processing_consents`
- `recording_upload_intents`
- `recording_deletion_outbox`
- `analysis_results.active_request_event_id`
- `analysis_results.analysis_outcome`
- `analysis_results.failure_code`
- `analysis_results.worker_revision`
- `analysis_results.pipeline_revision`
- `analysis_results.audio_sha256`
- `analysis_results.pronunciation_evidence_schema_version`
- `analysis_results.selected_phone`
- `analysis_results.selected_expected_index`
- `analysis_results.selected_start_ms`
- `analysis_results.selected_end_ms`
- `analysis_results.detector_score`
- `analysis_results.operating_threshold`
- `analysis_results.score_semantics`
- `analysis_results.evidence_state`
- visual supplement columns added by later migrations

### Impact

If this branch runs with:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

then the application can fail at startup because the entity model expects tables
and columns that the current DB backup does not have.

Likely failure categories:

- missing table: `analysis_request_outbox`
- missing table: `analysis_cancellation_outbox`
- missing table: `processing_consents`
- missing table: `recording_upload_intents`
- missing table: `recording_deletion_outbox`
- missing column on `analysis_results`
- missing column on `voice_recordings`

## `docs/database/schema.md` Gap

The project schema document is also not fully aligned with the current branch.

### Source Of Truth Range Is Stale

`docs/database/schema.md` says:

```md
current schema source of truth: `src/main/resources/db/migration/V0..V9`
```

However, the branch contains migrations up to `V14`.

Expected correction:

```md
current schema source of truth: `src/main/resources/db/migration/V0..V14`
```

### Missing `analysis_cancellation_outbox` Section

The branch has:

- `V12__create_analysis_cancellation_outbox.sql`
- `AnalysisCancellationOutbox.java`

But `docs/database/schema.md` does not document
`analysis_cancellation_outbox` as a table section.

This should be added if cancellation through Redis tombstones remains part of
the design.

### Incomplete `analysis_request_outbox`

`docs/database/schema.md` documents `analysis_request_outbox`, but misses columns
added by `V13__bind_analysis_outbox_to_stream_retention.sql`:

- `request_stream_id`
- `retention_protocol_version`

### Incomplete `analysis_results`

The current `AnalysisResult` entity contains more fields than the schema
document lists, including pronunciation evidence and visual supplement fields.

The document should include columns introduced by:

- `V9__store_pronunciation_evidence.sql`
- `V10__retain_restricted_visual_analysis_input.sql`
- `V11__store_same_attempt_visual_supplement.sql`

### Encoding Problem

The schema document has broken Korean text. This makes it difficult to review DB
meaning, enum meaning, and relationship notes.

Recommended action:

- restore the document as UTF-8
- keep table names, column names, enum values, and migration names in ASCII
- keep Korean descriptions readable

## Develop Convention Review

The branch mostly follows the established layered style:

- controller delegates to application services
- application services depend mostly on domain ports
- Redis, S3, ffmpeg, and HMAC details stay in infrastructure packages
- request/response DTOs stay under controller DTO packages
- AI worker message models are not reused as public API response DTOs

The following parts are still awkward against the project's existing
conventions.

### 1. `AnalysisJobPublisher` Ownership Is Ambiguous

Current file:

```text
src/main/java/org/example/voice/training/domain/port/AnalysisJobPublisher.java
```

Current dependency:

```java
import org.example.voice.analysis.domain.model.AnalysisWorkerRequest;
```

Problem:

`AnalysisJobPublisher` lives in the `training` domain package but accepts
`AnalysisWorkerRequest`, which is an `analysis` domain model and effectively an
AI worker transport model.

This means the `training` domain knows too much about the AI analysis worker
message shape.

Recommended options:

- Move `AnalysisJobPublisher` to `analysis.domain.port`.
- Or keep a training-side port but make it accept a training-owned command such
  as `SelectedRecordingAnalysisData` or `AnalysisRequestCommand`, then map to
  `AnalysisWorkerRequest` inside the analysis/infrastructure adapter.

For a minimal AI connection test, the second option may be simpler. For a
long-term analysis pipeline, the first option is cleaner.

### 2. `AnalysisResultJpaRepository` Is Under `training.infrastructure`

Current file:

```text
src/main/java/org/example/voice/training/infrastructure/AnalysisResultJpaRepository.java
```

Problem:

The entity is owned by:

```text
analysis.domain.entity.AnalysisResult
```

But the JPA repository lives under:

```text
training.infrastructure
```

This existed before the branch, but the `AI-API` branch increases its usage from
analysis-side infrastructure classes. That makes the package ownership problem
more visible.

Recommended correction:

- move `AnalysisResultJpaRepository` to `analysis.infrastructure`
- update training infrastructure adapters to import it from analysis
- keep application services depending on `AnalysisResultReader`,
  `AnalysisResultWriter`, `TrainingAnalysisReader`, or `TrainingAnalysisWriter`
  ports rather than the repository directly

### 3. Application Layer Imports Infrastructure Cache Names

Example:

```text
src/main/java/org/example/voice/analysis/application/AnalysisResultIngestionService.java
```

It imports:

```java
import org.example.voice.analysis.infrastructure.cache.AnalysisCacheNames;
```

Problem:

Application layer depending on `infrastructure.cache` weakens the intended
dependency direction.

This pattern also exists elsewhere in the project, so it is not unique to this
branch. Still, if the branch is being cleaned up, cache name constants would fit
better under:

- `analysis.application.cache`
- `analysis.domain.type`
- `common.cache`

### 4. Stream Components Directly Use JPA Repositories

Examples:

- `AnalysisRequestOutboxDispatcher`
- `AnalysisCancellationOutboxDispatcher`
- `AnalysisExecutionTimeoutSweeper`
- `AnalysisOperationsObserver`
- `AnalysisStreamRetentionSweeper`

These are infrastructure components, so direct repository use is acceptable.
However, if the project wants strict reader/writer DIP everywhere, these could
be wrapped behind infrastructure-facing ports.

Current recommendation:

- Do not refactor this immediately.
- Keep direct repository use in these scheduled infrastructure components unless
  the team wants a stricter architecture pass.

### 5. `FfmpegS3RecordingMediaNormalizer` Is Very Large

Current file:

```text
src/main/java/org/example/voice/training/infrastructure/storage/FfmpegS3RecordingMediaNormalizer.java
```

Problem:

It is a single infrastructure adapter, but it owns many responsibilities:

- S3 read/write
- local workspace management
- ffprobe execution
- ffmpeg execution
- sandbox command construction
- media validation
- digest generation
- cleanup

This is not necessarily a layer violation, but it is hard to review and test.

Recommended correction:

- keep the public port as `RecordingMediaNormalizationPort`
- split private collaborators only if this branch remains the production path
- possible internal components:
  - `MediaWorkspaceManager`
  - `FfmpegCommandRunner`
  - `MediaProbeReader`
  - `NormalizedObjectWriter`

For a short AI test branch, this whole component is probably too much.

## S3 And Object Storage Readiness

The branch does not actually require S3 by default.

Default configuration:

```yaml
storage:
  enabled: false
storage:
  media-normalization:
    enabled: false
analysis:
  stream:
    enabled: false
```

When `storage.enabled=false`, the development `PresignedUrlProvider` returns a
fake storage URL such as:

```text
https://storage.example.com/...
```

When `analysis.stream.enabled=true`, `AnalysisProductionConfigurationGuard`
requires:

- object storage enabled
- media normalization enabled
- Redis SSL enabled
- Redis password configured
- valid stream and DLQ names

This means:

- local upload URL shape can be tested without S3
- real Redis Stream analysis cannot be enabled safely without object storage and
  media normalization

If S3 is not currently built, this branch is too strict for simple integration
testing unless MinIO or another S3-compatible local storage is introduced.

## Required vs Optional DB Additions

For a minimal backend-AI Redis Stream test, the current DB can be used with much
less than the full `AI-API` schema.

### Minimal Required Existing Tables

- `voice_recordings`
- `analysis_results`
- `analysis_segments`

### Strongly Recommended Minimal Addition

- `analysis_results.active_request_event_id`

Reason:

This prevents a late result from an older request from overwriting the latest
retry result.

### Required For Production-Grade Outbox

- `analysis_request_outbox`

Reason:

This makes the DB transaction and Redis `XADD` delivery durable. Without it, a
crash between DB update and Redis publish can lose an analysis request.

### Required For Cancellation Flow

- `analysis_cancellation_outbox`

Reason:

This allows cancellation tombstones to be retried until Redis receives them.

### Required For S3 Presigned Upload Flow

- `recording_upload_intents`

Reason:

This tracks issued upload URLs and prevents registering arbitrary object keys.

### Required For Durable Object Deletion

- `recording_deletion_outbox`

Reason:

This makes DB deletion and object storage deletion eventually consistent.

### Required For Consent/Audit-Heavy Design

- `processing_consents`

Reason:

This preserves proof that voice or visual processing was authorized for a
specific request generation.

## Recommended Direction

### If The Goal Is Quick AI Connection Test

Prefer a smaller design:

1. Keep the existing frontend-backend flow.
2. Create or reuse `analysis_results` with `PENDING`.
3. Publish Redis Stream request with:
   - `analysisId`
   - `recordingId`
   - `audioUrl` or temporary accessible audio location
   - `scriptText`
   - `learningFocus`
   - `requestEventId` if added
4. AI server consumes the request stream.
5. AI server publishes result stream.
6. Backend consumer updates `analysis_results` and `analysis_segments`.

Avoid for now:

- S3 adapter
- ffmpeg normalization
- HMAC grant
- processing consent ledger
- cancellation outbox
- retention sweeper
- production guard

This is easier to match with the current DB backup.

### If The Goal Is Production AI Pipeline

Keep the `AI-API` direction, but finish the schema and documentation alignment:

1. Apply or regenerate migrations against the real DB.
2. Update `docs/database/schema.md` to `V0..V14`.
3. Add `analysis_cancellation_outbox` to the schema document.
4. Add missing `analysis_request_outbox` columns.
5. Add all missing `analysis_results` evidence and visual supplement columns.
6. Move `AnalysisResultJpaRepository` to `analysis.infrastructure`.
7. Reconsider `AnalysisJobPublisher` package ownership.
8. Decide whether S3 will be AWS S3, Cloudflare R2, NCP Object Storage, or
   local MinIO for development.
9. Make `docs/api/ai-redis-stream-contract.md` match the DB persistence model.

## Priority Fix List

### High

- Decide whether this branch is for quick testing or production pipeline.
- Align actual DB schema with the branch before enabling `ANALYSIS_STREAM_ENABLED=true`.
- Fix `docs/database/schema.md` source-of-truth range.
- Document `analysis_cancellation_outbox`.
- Add missing `analysis_request_outbox` and `analysis_results` columns to
  `docs/database/schema.md`.

### Medium

- Move `AnalysisResultJpaRepository` to `analysis.infrastructure`.
- Revisit `AnalysisJobPublisher` ownership and input model.
- Move cache name constants out of infrastructure if doing a cleanup pass.
- Restore broken Korean encoding in DB docs.

### Low

- Split `FfmpegS3RecordingMediaNormalizer` into smaller private infrastructure
  collaborators.
- Add a lightweight local integration profile if the team keeps Redis Stream but
  does not yet have S3.

## Final Recommendation

For the current team situation, the safest path is:

1. Do not use the full `AI-API` branch as the immediate test baseline.
2. Use the existing DB shape and add only `active_request_event_id` if stale
   async results are a concern.
3. Implement a minimal Redis Stream request/result consumer first.
4. Add DB outbox, consent, S3, normalization, retention, and metrics later when
   the production AI pipeline is actually being prepared.

The current `AI-API` branch is not wrong, but it is larger and stricter than the
current DB and immediate testing goal.
