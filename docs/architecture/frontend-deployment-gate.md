# Frontend Deployment Gate

The backend deployment profile may retain a historical `frontend` section for
audit purposes, but frontend revision and Next.js asset hashes are not a
blocking backend deployment contract.

Backend release checks continue to enforce the AI verifier, pinned schemas,
service readiness, drain state, and rollback invariants. A Vercel redeploy or
static frontend asset change must not stop an otherwise compatible backend
release.

API or analysis-contract compatibility changes must be represented by the
existing backend, schema, verifier, or coordinated release checks instead of
by pinning frontend build asset paths.
