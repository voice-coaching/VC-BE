# Deployment Compatibility Gates

The deployment profile may contain historical frontend and schema metadata,
but those values are not used as blocking checks for a backend JAR release.

The release still verifies the uploaded JAR SHA-256, release state, service
readiness, drain state, and rollback invariants. Frontend build assets and
contract-file digests can change independently of the backend release and must
not prevent an otherwise valid deployment.

Changes that require coordinated rollout must be checked by the owning
service readiness and integration tests rather than by stale digests copied
into the EC2 runtime profile.
