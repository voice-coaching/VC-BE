-- Snapshot existing active members once. This is a TEST usage tier, never users.role=ADMIN.
-- Signups wait until commit and receive no grant, regardless of their generated user ID.
LOCK TABLE users IN SHARE MODE;

CREATE TABLE user_test_membership_grants (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    granted_at TIMESTAMPTZ NOT NULL
);

INSERT INTO user_test_membership_grants(user_id, granted_at)
SELECT id, CURRENT_TIMESTAMP
FROM users
WHERE status = 'ACTIVE';
