DELETE FROM social_accounts
WHERE user_id IN (
    SELECT id
    FROM users
    WHERE status = 'WITHDRAWN'
);

UPDATE users
SET email = NULL,
    password = NULL
WHERE status = 'WITHDRAWN'
  AND (email IS NOT NULL OR password IS NOT NULL);
