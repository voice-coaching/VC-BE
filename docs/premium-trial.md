# Signup premium trial

Authenticated endpoints (no caller-supplied user ID or expiration):

- `GET /api/users/me/premium-trial`: read-only eligibility/status.
- `POST /api/users/me/premium-trial`: explicitly activate the authenticated account's trial.

Both use the normal ApiResponse envelope and `Cache-Control: no-store`.
Data fields: `status` (`ELIGIBLE`, `INELIGIBLE`, `ACTIVE`, `EXPIRED`), `eligible`, `startsAt`, `expiresAt`.
Dates are null before activation; active/expired grants retain their original dates.

An active, currently free account with no trial row may activate. There is no signup-date cutoff; existing free users without a trial are eligible too. Signup and viewing the offer do not grant membership. Existing paid/test-admin accounts cannot create a trial. Suspended/withdrawn accounts are rejected.

Activation locks the users row inside the transaction, serializing with payment updates and concurrent activation. Flyway V71 creates a per-user primary key and a PostgreSQL exact 72-hour constraint. The server clock supplies start/end. Retrying an active grant returns the original dates; expired grants return PREMIUM_TRIAL_ALREADY_USED (409). Ineligible membership returns PREMIUM_TRIAL_NOT_ELIGIBLE (409). An aborted client response can safely retry the POST.

Membership reads (including batch/admin reads) resolve an active trial to paid/TRIAL; coaching resolves it to an unlimited window with the same expiration. Existing paid purchases and test-admin grants retain precedence. At the exact expiration the trial no longer contributes membership or coaching entitlement; with no other grant, membership resolves to free. Keep the expired row to prevent reuse. No scheduled job, role mutation, payment order or automatic billing is involved.

Deploy the backend with Flyway V71 before the new frontend. The frontend suppresses the offer when eligibility cannot be loaded. Production migration execution and real-account activation must be checked through the normal deployment process; local verification does not grant a production benefit.

Validation: PremiumTrialIntegrationTest uses real H2 transactions/row locks for activation, concurrency, rollback, exact expiry and authenticated owner-scoped HTTP tests. PremiumTrialServiceTest covers rejection of an existing paid member. TestMembershipServiceTest covers preexisting membership precedence. PostgreSQL migration execution was not locally exercised (no running PostgreSQL/Docker service).

Existing separate issue: CoachingUsageService.reserve on base main already lacks quota/period/input rejection checks. This change adds the entitlement and expiration calculation but does not restore unrelated admission rules. Membership becomes free on expiry; enforcement of ordinary free quotas needs a separate fix. Existing CoachingUsageServiceTest failures should not be presented as passing trial validation.
