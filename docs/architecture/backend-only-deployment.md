# Backend-Only Deployment

The production backend deployment no longer requires an external RunPod
readiness check or an AI runtime profile. The deployment entry point remains
`scripts/deploy.sh` because CI invokes it on the EC2 host.

The release flow is:

1. Verify the uploaded JAR SHA-256.
2. Drain active backend database work.
3. Activate the versioned JAR and restart `alpha-backend.service`.
4. Poll `http://127.0.0.1:9091/internal/actuator/health` until the backend is
   `UP`.
5. Recheck the database drain state and mark the release complete.

If startup or local health fails, the existing release state and rollback path
remain available. RunPod endpoint URLs, AI tokens, external worker readiness,
and frontend/schema asset pins are not deployment prerequisites.
