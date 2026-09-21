ALTER TABLE orders ADD COLUMN offline_operation_id TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_offline_operation
ON orders(offline_operation_id)
WHERE offline_operation_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS offline_operations (
  user_id INTEGER NOT NULL,
  operation_id TEXT NOT NULL,
  method TEXT NOT NULL,
  path TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'processing'
    CHECK (status IN ('processing','done')),
  response_status INTEGER,
  response_body TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, operation_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_offline_operations_created
ON offline_operations(created_at);

CREATE INDEX IF NOT EXISTS idx_offline_operations_status
ON offline_operations(status, updated_at);
