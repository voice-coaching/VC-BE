# main/develop synchronization — 2026-11-03

Issue #179 tracks merging the independently updated branches without losing
either history. The integration branch starts from develop and merges main.

## Reviewed starting points

- develop: `38c65a6a46f1e75285f1cb77c63c50a08b56d6b6` (PR #176).
- main: `e3247c6` (including PR #177 administrator work and PR #178, #181–185
  delivery, ownership/contract, administrator read and startup recovery changes).
- Common ancestor: `7e05c29c6005c05838c2dae658cae7fca136668f`.

The initial main/develop difference is not an instruction to restore older
develop implementations over the current main policies. Main's administrator,
authentication, analysis, deployment and migration changes are retained.

## Merge decisions

`RunPodAnalysisRequestOutboxDispatcher` has two conflicting sections. Keep the
current main checks for the exact request/execution IDs and the already claimed
or handoff-received execution. Preserve develop's due-row selection and immediate
endpoint gate release, which main's recovery already includes. The worker HTTP
call stays outside the database transaction, and retry backoff stays on the row.

`AnalysisRequestOutbox` merges textually but duplicates the identical three-argument
`recordDispatchFailure` method. Keep exactly one copy of the bounded HTTP retry
method, including Retry-After. Preserve main's separate Redis Stream behavior and
other policies. No production method is changed relative to the reviewed main.

The develop outbox troubleshooting note is retained with a link to the newer
main recovery record, distinguishing the original observations from verified
production findings. Existing tests are updated to call the due-row repository
method. Added regression coverage checks ready rows behind a deferred row in
real PostgreSQL, transaction-free HTTP, bounded retries, expiry, generation
changes during delivery and claimed/handoff-received execution preservation.

## Verification and handoff

Before publishing, compare production sources, migrations, contract resources,
build settings and deployment workflows against the reviewed main. Compile all
test sources and build the executable JAR, execute the related regression tests,
and record any broader regression results separately. Main's current CI only
compiles and packages; a green check there does not mean all tests executed.

1. Review and merge the integration PR into **develop** through the team workflow.
2. Have the team open and merge **develop → main** after checking for concurrent
   branch updates. Do not push directly to main or force-update either branch.
3. Fetch both refs and confirm `git diff --exit-code origin/main origin/develop`
   is empty. Merge commits can differ while the file trees are identical.

Both branches' commits must remain ancestors of the integration commit. New
changes on either branch require another comparison before the final main PR.
The integration does not introduce endpoints, migrations, signed AI contract
changes or operations against the running service. Team merges may trigger the
repository's existing deployment workflow.

## Recorded validation outcome

The reviewed `c0f940e` integration passes all eleven related regression tests with
real isolated PostgreSQL and passes full production/test compilation and bootJar.
The cancellation fixture waits for main's asynchronous delivery completion and
closes the executor; it preserves the original execution and no-HTTP-transaction
assertions. Production files still exactly match this main revision.

A broader run against the earlier `ae254ff` main baseline is failed and incomplete:
542 recorded tests, 279 pass, 261 fail, two skip. The local worker was stopped after
an extended context-resource scan while preparing TitleIntegrationTest. Unmodified
latest main `c0f940e`, extracted into a separate source directory, reproduces four
representative failures in AnalysisServiceTest/FeedbackRegenerationServiceTest
(six tests, two pass/four fail). All remaining failures have not been individually
diagnosed. Do not describe the full suite as passing. The user explicitly approved
publishing with these failed/incomplete results disclosed while preserving main
policies. This does not authorize automatic PR merging or policy changes.

During that approval main advanced again; the integration baseline is pinned to
`e3247c6`. The newer main changes are incorporated through an additional merge,
with a fresh related regression/build verification. The broad failed/incomplete
run above was against `ae254ff`, and the independent four-failure reproduction
was against `c0f940e`; neither is a completed full regression of `e3247c6`.
The final `e3247c6` verification passes all eleven related tests without skips,
including real PostgreSQL, and full production/test compilation plus bootJar.
