ALTER TABLE users ADD COLUMN is_owner INTEGER NOT NULL DEFAULT 0
  CHECK (is_owner IN (0,1));

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_single_owner
ON users(is_owner)
WHERE is_owner=1;

UPDATE users
SET is_owner=1
WHERE id=(
  SELECT id
  FROM users
  WHERE role='admin' AND active=1
  ORDER BY id ASC
  LIMIT 1
)
AND NOT EXISTS (
  SELECT 1 FROM users WHERE is_owner=1
);
